package com.scomarlf.index.bot

import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ChatAction
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.*
import com.scomarlf.index.datasource.Elasticsearch
import com.scomarlf.index.datasource.Reply
import com.scomarlf.index.datasource.Telegram
import com.scomarlf.index.provider.BotProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import kotlin.collections.HashMap

@Service
class Secretary(
    private val botProvider: BotProvider,
    private val reply: Reply,
    private val telegram: Telegram,
    private val elasticsearch: Elasticsearch
) {
    private data class Await(val callbackData: String)

    private val logger = LoggerFactory.getLogger(Secretary::class.java)
    private val awaitStatus = HashMap<Long, Await>()

    init {
        subscribeUpdate()
    }

    private fun subscribeUpdate() {
        botProvider.updateObservable.subscribe(
            { update ->
                try {
                    if (update.callbackQuery() == null && update.message().chat().type() != Chat.Type.Private)
                        return@subscribe
                    // 输入状态
                    val chatId =
                        if (update.callbackQuery() != null) update.callbackQuery().from().id()
                        else update.message().chat().id()
                    val chatAction = SendChatAction(chatId, ChatAction.typing)
                    botProvider.send(chatAction)
                    // 回执
                    when (true) {
                        update.callbackQuery() != null -> executeByButton(update)
                        update.message().text().startsWith("/") -> executeByCommand(update)
                        update.message().text().startsWith("@") -> executeByEnroll(update)
                        update.message().text().startsWith("https://t.me/") -> executeByEnroll(update)
                        else -> executeByText(update)
                    }
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Secretary.error")
            },
            {
                logger.error("Secretary.complete")
            }
        )
    }

    private fun executeByText(update: Update) {
        val sendMessage = SendMessage(825561116, "text")
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByEnroll(update: Update) {
        // 获取会话ID和收录内容
        val chatId = update.message().chat().id()
        val id = update.message().text().replaceFirst("@", "").replaceFirst("https://t.me/", "")
        // 回执
        val telegramMod = telegram.getModFromWeb(id)
        val sendMessage = when (telegramMod) {
            is Telegram.TelegramGroup -> {
                // 提示添加至群

                SendMessage(chatId, "msg")
            }
            is Telegram.TelegramChannel, is Telegram.TelegramBot -> {
                val enroll = Elasticsearch.Enroll(
                    UUID.randomUUID().toString(),
                    telegramMod.title,
                    telegramMod.about,
                    null,
                    null,
                    telegramMod.id,
                    null,
                    Date().time,
                    update.message().chat().id(),
                    update.message().chat().username(),
                    false
                )
                val createEnroll = elasticsearch.addEnroll(enroll)
                if (!createEnroll) return
                val msg = botProvider.makeRecordDetail(telegramMod)
                val keyboard = botProvider.makeEnrollKeyboardMarkup(enroll.id)
                SendMessage(chatId, msg).disableWebPagePreview(true).parseMode(ParseMode.HTML).replyMarkup(keyboard)
            }
            else -> SendMessage(chatId, reply.message["nothing"])
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByCommand(update: Update) {
        // 获取会话ID和命令内容
        val chatId = update.message().chat().id()
        val cmd = update.message().text().replaceFirst("/", "")
        // 回执
        val sendMessage = when (cmd) {
            "start", "enroll", "update", "help" -> SendMessage(chatId, reply.message[cmd])
            "list" -> {
                val keyboard = botProvider.makeReplyKeyboardMarkup()
                SendMessage(chatId, reply.message[cmd]).replyMarkup(keyboard)
            }
            "cancel" -> {
                awaitStatus.remove(chatId)
                SendMessage(chatId, reply.message["cancel"])
            }
            else -> SendMessage(chatId, reply.message["can-not-understand"])
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByButton(update: Update) {
        val answer = AnswerCallbackQuery(update.callbackQuery().id())
        botProvider.send(answer)

        val chatId = update.callbackQuery().from().id().toLong()
        val callbackData = update.callbackQuery().data()
        when (true) {
            callbackData.startsWith("enroll"), callbackData.startsWith("classification") -> {
                executeByEnrollButton(update)
            }
            callbackData.startsWith("page") -> {

            }
        }
    }

    private fun executeByEnrollButton(update: Update) {
        val chatId = update.callbackQuery().from().id().toLong()
        val messageId = update.callbackQuery().message().messageId()
        val callbackData = update.callbackQuery().data()
        val callbackDataVal = callbackData.replace("enroll:", "").replace("classification:", "").split("&")
        val field = callbackDataVal[0]
        val id = callbackDataVal[1]
        when (true) {
            // 通过按钮修改收录申请信息
            callbackData.startsWith("classification") -> {
                // 修改数据
                val enroll = elasticsearch.getEnroll(id)!!
                val newEnroll = enroll.copy(classification = field)
                elasticsearch.updateEnroll(newEnroll)
                // 删除上一条消息
                val deleteMessage = DeleteMessage(chatId, messageId)
                botProvider.send(deleteMessage)
                // 回执新消息
                val detail = botProvider.makeRecordDetail(newEnroll)
                val keyboard = botProvider.makeEnrollKeyboardMarkup(id)
                val msg = SendMessage(chatId, detail).replyMarkup(keyboard)
                msg.parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
                botProvider.send(msg)
            }
            // 通过文字修改收录申请信息
            arrayOf("title", "about", "tags").contains(field) -> {
                this.awaitStatus[chatId] = Await(callbackData)
                val msg = SendMessage(chatId, reply.message["enroll-update-$field"])
                botProvider.send(msg)
            }
            // 通过按钮修改收录申请信息
            field == "classification" -> {
                // 删除上一条消息
                val deleteMessage = DeleteMessage(chatId, messageId)
                botProvider.send(deleteMessage)
                // 回执新消息
                val enroll = elasticsearch.getEnroll(id)!!
                val detail = botProvider.makeRecordDetail(enroll)
                val keyboard = botProvider.makeInlineKeyboardMarkup(id)
                val msg = SendMessage(chatId, detail)
                msg.parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
                botProvider.send(msg)
            }
            // 提交
            field == "submit" -> {
                val enroll = elasticsearch.getEnroll(id)!!
                val newEnroll = enroll.copy(status = true)
                elasticsearch.updateEnroll(newEnroll)
                val deleteMessage = DeleteMessage(chatId, messageId)
                botProvider.send(deleteMessage)
                val msg = SendMessage(chatId, reply.message["enroll-submit"])
                botProvider.send(msg)
            }
            // 取消
            field == "cancel" -> {
                elasticsearch.deleteEnroll(id)
                val deleteMessage = DeleteMessage(chatId, messageId)
                botProvider.send(deleteMessage)
                val msg = SendMessage(chatId, reply.message["cancel"])
                botProvider.send(msg)
            }
        }
    }
}