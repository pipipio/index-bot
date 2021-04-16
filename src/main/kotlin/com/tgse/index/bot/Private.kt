package com.tgse.index.bot

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.*
import com.tgse.index.bot.execute.EnrollAndApproveExecute
import com.tgse.index.datasource.AwaitStatus
import com.tgse.index.datasource.Elasticsearch
import com.tgse.index.datasource.Telegram
import com.tgse.index.factory.MsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import com.tgse.index.provider.WatershedProvider.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * 私聊
 */
@Service
class Private(
    private val enrollAndApproveExecute: EnrollAndApproveExecute,
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider,
    private val msgFactory: MsgFactory,
    private val elasticsearch: Elasticsearch,
    private val telegram: Telegram,
    private val awaitStatus: AwaitStatus
) {

    private val logger = LoggerFactory.getLogger(Private::class.java)

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
                        awaitStatus.getAwaitStatus(request.chatId) != null ->
                            enrollAndApproveExecute.executeByStatus(EnrollAndApproveExecute.Type.Enroll, request)
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
                msgFactory.makeEnrollMsg(request.chatId, enroll.uuid)
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
                msgFactory.makeEnrollMsg(request.chatId, enroll.uuid)
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
                enrollAndApproveExecute.executeByEnrollButton(EnrollAndApproveExecute.Type.Enroll, request)
            }
            callbackData.startsWith("page") -> {

            }
        }
    }
}