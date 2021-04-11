package com.tgse.index.bot

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.response.SendResponse
import com.tgse.index.factory.MsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import com.tgse.index.provider.WatershedProvider.BotGroupRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
class Group(
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider,
    private val msgFactory: MsgFactory,
    @Value("\${group.general.autoDeleteMsgCycle}")
    private val autoDeleteMsgCycle: Long
) {

    data class MessageSent(val chatId: Long, val messageId: Int, val time: Long)

    private val logger = LoggerFactory.getLogger(Group::class.java)
    private val autoDeleteMsgThread = Thread({ autoDeleteMessageSent() }, "消息自毁线程")
    private val messageSents = mutableListOf<MessageSent>()

    init {
        subscribeUpdate()

        autoDeleteMsgThread.isDaemon = true
        autoDeleteMsgThread.start()
    }

    private fun subscribeUpdate() {
        watershedProvider.requestObservable.subscribe(
            { request ->
                try {
                    if (request !is BotGroupRequest) return@subscribe
                    // 输入状态
                    botProvider.sendTyping(request.chatId)
                    // 回执
                    when (true) {
                        request.update.callbackQuery() != null -> executeByButton(request)
                        request.update.message().text().startsWith("/") &&
                                request.update.message().text().endsWith("@${botProvider.username}") ->
                            executeByCommand(request)
                        else -> executeByText(request)
                    }
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Group.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Group.complete")
            }
        )
    }

    private fun executeByText(request: BotGroupRequest) {

    }

    private fun executeByCommand(request: BotGroupRequest) {
        // 获取命令内容
        val cmd = request.update.message().text().replaceFirst("/", "").replace("@${botProvider.username}", "")
        // 回执
        val sendMessage = when (cmd) {
            "start" -> msgFactory.makeReplyMsg(request.chatId, "only-private")
            // todo： 获取群组信息，然后回执给管理员
            "enroll", "update" -> msgFactory.makeReplyMsg(request.chatId, cmd)
            // todo: 开启后，可以在群组中查找群组……
            "list" -> msgFactory.makeListReplyMsg(request.chatId)
            "mine" -> msgFactory.makeReplyMsg(request.chatId, "only-private")
            // todo： 配置是否开启索引服务群组 /list
            "setting" -> msgFactory.makeReplyMsg(request.chatId, "setting")
            "help" -> msgFactory.makeReplyMsg(request.chatId, "help-group")
            else -> msgFactory.makeReplyMsg(request.chatId, "can-not-understand")
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        val sendResponse = botProvider.send(sendMessage)
        saveMessageSent(sendResponse)
    }

    private fun executeByButton(request: BotGroupRequest) {

    }

    private fun saveMessageSent(sendResponse: SendResponse) {
        if (sendResponse.isOk) {
            val ms = MessageSent(sendResponse.message().chat().id(), sendResponse.message().messageId(), Date().time)
            messageSents.add(ms)
        }
    }

    private fun autoDeleteMessageSent() {
        while (true) {
            Thread.sleep(2000)
            val now = Date().time
            messageSents.removeIf { messageSent ->
                try {
                    if (messageSent.time + autoDeleteMsgCycle < now) {
                        botProvider.sendDeleteMessage(messageSent.chatId, messageSent.messageId)
                        true
                    } else {
                        false
                    }
                } catch (e: Throwable) {
                    true
                }
            }
        }
    }

}