package com.tgse.index.bot

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.*
import com.tgse.index.MismatchException
import com.tgse.index.datasource.Elasticsearch
import com.tgse.index.datasource.Telegram
import com.tgse.index.factory.MsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import com.tgse.index.provider.WatershedProvider.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*
import kotlin.collections.HashMap

/**
 * 私聊
 */
@Service
class Private(
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider,
    private val msgFactory: MsgFactory,
    private val elasticsearch: Elasticsearch,
    private val telegram: Telegram
) {
    private data class Await(val messageId: Int, val callbackData: String)

    private val logger = LoggerFactory.getLogger(Private::class.java)
    private val awaitStatus = HashMap<Long, Await>()

    init {
        subscribeUpdate()
    }

    private fun subscribeUpdate() {
        watershedProvider.requestObservable.subscribe(
            { request ->
                try {
                    if (request !is BotPrivateRequest) return@subscribe
                    // 输入状态
                    botProvider.sendTyping(request.chatId)
                    // 回执
                    when (true) {
                        request.update.callbackQuery() != null -> executeByButton(request)
                        awaitStatus[request.chatId] != null -> executeByStatus(request)
                        request.update.message().text().startsWith("/") -> executeByCommand(request)
                        request.update.message().text().startsWith("@") -> executeByEnroll(request)
                        request.update.message().text().startsWith("https://t.me/") -> executeByEnroll(request)
                        else -> executeByText(request)
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

    private fun executeByText(request: BotPrivateRequest) {
        val sendMessage = SendMessage(825561116, "text")
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByEnroll(request: BotPrivateRequest) {
        // 获取收录内容
        val id = request.update.message().text().replaceFirst("@", "").replaceFirst("https://t.me/", "")
        // 回执
        val telegramMod = telegram.getTelegramModFromWeb(id)
        val sendMessage = when (telegramMod) {
            is Telegram.TelegramGroup -> {
                msgFactory.makeReplyMsg(request.chatId, "enroll-need-join-group")
            }
            is Telegram.TelegramChannel -> {
                val enroll = Elasticsearch.Enroll(
                    UUID.randomUUID().toString(),
                    Telegram.TelegramModType.Channel,
                    null,
                    telegramMod.title,
                    telegramMod.description,
                    null,
                    null,
                    telegramMod.username,
                    null,
                    telegramMod.members,
                    Date().time,
                    request.chatId,
                    request.update.message().chat().username(),
                    false
                )
                val createEnroll = elasticsearch.addEnroll(enroll)
                if (!createEnroll) return
                msgFactory.makeEnrollMsg(request.chatId, telegramMod, enroll.uuid)
            }
            is Telegram.TelegramBot -> {
                val enroll = Elasticsearch.Enroll(
                    UUID.randomUUID().toString(),
                    Telegram.TelegramModType.Bot,
                    null,
                    telegramMod.title,
                    telegramMod.description,
                    null,
                    null,
                    telegramMod.username,
                    null,
                    null,
                    Date().time,
                    request.chatId,
                    request.update.message().chat().username(),
                    false
                )
                val createEnroll = elasticsearch.addEnroll(enroll)
                if (!createEnroll) return
                msgFactory.makeEnrollMsg(request.chatId, telegramMod, enroll.uuid)
            }
            else -> msgFactory.makeReplyMsg(request.chatId, "nothing")
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByCommand(request: BotPrivateRequest) {
        // 获取命令内容
        val cmd = request.update.message().text().replaceFirst("/", "")
        // 回执
        val sendMessage = when (cmd) {
            "start" -> msgFactory.makeReplyMsg(request.chatId, "start")
            "enroll" -> msgFactory.makeReplyMsg(request.chatId, cmd)
            "update", "mine" -> msgFactory.makeReplyMsg(request.chatId, "await")
            "list" -> msgFactory.makeListReplyMsg(request.chatId)
            "setting" -> msgFactory.makeReplyMsg(request.chatId, "only-group")
            "help" -> msgFactory.makeReplyMsg(request.chatId, "help-private")
            else -> msgFactory.makeReplyMsg(request.chatId, "can-not-understand")
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByButton(request: BotPrivateRequest) {
        val answer = AnswerCallbackQuery(request.update.callbackQuery().id())
        botProvider.send(answer)

        val callbackData = request.update.callbackQuery().data()
        when (true) {
            callbackData.startsWith("enroll"), callbackData.startsWith("classification") -> {
                executeByEnrollButton(request)
            }
            callbackData.startsWith("page") -> {

            }
        }
    }

    private fun executeByEnrollButton(request: BotPrivateRequest) {
        val callbackData = request.update.callbackQuery().data()
        val callbackDataVal = callbackData.replace("enroll:", "").replace("classification:", "").split("&")
        val field = callbackDataVal[0]
        val uuid = callbackDataVal[1]
        when (true) {
            // 通过按钮修改收录申请信息
            callbackData.startsWith("classification") -> {
                // 修改数据
                val enroll = elasticsearch.getEnroll(uuid)!!
                val newEnroll = enroll.copy(classification = field)
                elasticsearch.updateEnroll(newEnroll)
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId, request.messageId!!)
                // 回执新消息
                val msg = msgFactory.makeEnrollMsg(request.chatId, uuid)
                botProvider.send(msg)
            }
            // 通过文字修改收录申请信息
            arrayOf("title", "about", "tags").contains(field) -> {
                awaitStatus[request.chatId] = Await(request.messageId!!, callbackData)
                val msg = msgFactory.makeReplyMsg(request.chatId, "enroll-update-$field")
                botProvider.send(msg)
            }
            // 通过按钮修改收录申请信息
            field == "classification" -> {
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId, request.messageId!!)
                // 回执新消息
                val msg = msgFactory.makeEnrollChangeClassificationMsg(request.chatId, uuid)
                botProvider.send(msg)
            }
            // 提交
            field == "submit" -> {
                val enroll = elasticsearch.getEnroll(uuid)!!
                val newEnroll = enroll.copy(status = true)
                elasticsearch.updateEnroll(newEnroll)
                botProvider.sendDeleteMessage(request.chatId, request.messageId!!)
                clearAwaitStatus(request.chatId)
                val msg = msgFactory.makeReplyMsg(request.chatId, "enroll-submit")
                botProvider.send(msg)
            }
            // 取消
            field == "cancel" -> {
                elasticsearch.deleteEnroll(uuid)
                botProvider.sendDeleteMessage(request.chatId, request.messageId!!)
                clearAwaitStatus(request.chatId)
                val msg = msgFactory.makeReplyMsg(request.chatId, "cancel")
                botProvider.send(msg)
            }
        }
    }

    private fun executeByStatus(request: BotPrivateRequest) {
        val statusCallbackData = awaitStatus[request.chatId]!!.callbackData
        val callbackDataVal = statusCallbackData.replace("enroll:", "").split("&")
        val field = callbackDataVal[0]
        val uuid = callbackDataVal[1]
        val msgContent = request.update.message().text()
        try {
            val enroll = elasticsearch.getEnroll(uuid)!!
            when (field) {
                "title" -> {
                    if (msgContent.length > 26) throw MismatchException("标题太长，修改失败")
                    val newEnroll = enroll.copy(title = msgContent)
                    elasticsearch.updateEnroll(newEnroll)
                }
                "about" -> {
                    val newEnroll = enroll.copy(description = msgContent)
                    elasticsearch.updateEnroll(newEnroll)
                }
                "tags" -> {
                    val tags = mutableListOf<String>()
                    """(?<=#)[^\s#]+""".toRegex().findAll(msgContent).forEach {
                        val tag = "#${it.value}"
                        if (!tags.contains(tag))
                            tags.add(tag)
                    }
                    if (tags.size < 1) throw MismatchException("格式有误，修改失败")
                    val newEnroll = enroll.copy(tags = tags)
                    elasticsearch.updateEnroll(newEnroll)
                }
            }
            // 清除状态
            applyAwaitStatus(request.chatId)
            // 回执新消息
            val msg = msgFactory.makeEnrollMsg(request.chatId, enroll.uuid)
            botProvider.send(msg)
        } catch (e: Throwable) {
            when (e) {
                is MismatchException -> {
                    val msg = msgFactory.makeExceptionMsg(request.chatId, e)
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