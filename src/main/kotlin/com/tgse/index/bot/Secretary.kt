package com.tgse.index.bot

import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ChatAction
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.*
import com.tgse.index.MismatchException
import com.tgse.index.datasource.Elasticsearch
import com.tgse.index.datasource.Telegram
import com.tgse.index.factory.MsgFactory
import com.tgse.index.provider.BotProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import kotlin.collections.HashMap

@Service
class Secretary(
    private val botProvider: BotProvider,
    private val msgFactory: MsgFactory,
    private val elasticsearch: Elasticsearch,
    private val telegram: Telegram
) {
    private data class Await(val messageId: Int, val callbackData: String)

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
                        if (update.callbackQuery() != null) update.callbackQuery().from().id().toLong()
                        else update.message().chat().id()
                    val chatAction = SendChatAction(chatId, ChatAction.typing)
                    botProvider.send(chatAction)
                    // 回执
                    when (true) {
                        update.callbackQuery() != null -> executeByButton(update)
                        awaitStatus[chatId] != null -> executeByStatus(update)
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
                botProvider.sendErrorMessage(throwable)
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
                // todo：提示添加至群
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
                msgFactory.makeEnrollMsg(chatId, telegramMod, enroll.id)
            }
            else -> msgFactory.makeReplyMsg(chatId, "nothing")
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
            "start", "enroll", "update", "help" -> msgFactory.makeReplyMsg(chatId, cmd)
            "list" -> msgFactory.makeListReplyMsg(chatId)
            else -> msgFactory.makeReplyMsg(chatId, "can-not-understand")
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
                botProvider.sendDeleteMessage(chatId, messageId)
                // 回执新消息
                val msg = msgFactory.makeEnrollMsg(chatId, id)
                botProvider.send(msg)
            }
            // 通过文字修改收录申请信息
            arrayOf("title", "about", "tags").contains(field) -> {
                awaitStatus[chatId] = Await(messageId, callbackData)
                val msg = msgFactory.makeReplyMsg(chatId, "enroll-update-$field")
                botProvider.send(msg)
            }
            // 通过按钮修改收录申请信息
            field == "classification" -> {
                // 删除上一条消息
                botProvider.sendDeleteMessage(chatId, messageId)
                // 回执新消息
                val msg = msgFactory.makeEnrollChangeClassificationMsg(chatId, id)
                botProvider.send(msg)
            }
            // 提交
            field == "submit" -> {
                val enroll = elasticsearch.getEnroll(id)!!
                val newEnroll = enroll.copy(status = true)
                elasticsearch.updateEnroll(newEnroll)
                applyAwaitStatus(chatId)
                val msg = msgFactory.makeReplyMsg(chatId, "enroll-submit")
                botProvider.send(msg)
            }
            // 取消
            field == "cancel" -> {
                elasticsearch.deleteEnroll(id)
                applyAwaitStatus(chatId)
                val msg = msgFactory.makeReplyMsg(chatId, "cancel")
                botProvider.send(msg)
            }
        }
    }

    private fun executeByStatus(update: Update) {
        val chatId = update.message().chat().id()
        val statusCallbackData = awaitStatus[chatId]!!.callbackData
        val callbackDataVal = statusCallbackData.replace("enroll:", "").split("&")
        val field = callbackDataVal[0]
        val id = callbackDataVal[1]
        val msgContent = update.message().text()
        try {
            val enroll = elasticsearch.getEnroll(id)!!
            when (field) {
                "title" -> {
                    if (msgContent.length > 26) throw MismatchException("标题太长，修改失败")
                    val newEnroll = enroll.copy(title = msgContent)
                    elasticsearch.updateEnroll(newEnroll)
                }
                "about" -> {
                    val newEnroll = enroll.copy(about = msgContent)
                    elasticsearch.updateEnroll(newEnroll)
                }
                "tags" -> {
                    val tags = mutableListOf<String>()
                    """(?<=#)[^\s#]+""".toRegex().findAll(msgContent).forEach {
                        tags.add("#${it.value}")
                    }
                    if (tags.size < 1) throw MismatchException("格式有误，修改失败")
                    val newEnroll = enroll.copy(tags = tags)
                    elasticsearch.updateEnroll(newEnroll)
                }
            }
            // 清除状态
            applyAwaitStatus(chatId)
            // 回执新消息
            val msg = msgFactory.makeEnrollMsg(chatId, enroll.id)
            botProvider.send(msg)
        } catch (e: Throwable) {
            when (e) {
                is MismatchException -> {
                    val msg = msgFactory.makeExceptionMsg(chatId, e)
                    botProvider.send(msg)
                }
                else -> throw  e
            }
        }
    }

    /**
     * 状态处理完毕
     */
    private fun applyAwaitStatus(chatId: Long) {
        val chatAwaitStatus = awaitStatus[chatId]
        if (chatAwaitStatus != null) {
            botProvider.sendDeleteMessage(chatId, chatAwaitStatus.messageId)
            awaitStatus.remove(chatId)
        }
    }

    /**
     * 取消状态
     */
    private fun clearAwaitStatus(chatId: Long) {
        val chatAwaitStatus = awaitStatus[chatId]
        if (chatAwaitStatus != null) {
            awaitStatus.remove(chatId)
        }
    }
}