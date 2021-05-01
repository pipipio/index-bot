package com.tgse.index.bot

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.tgse.index.bot.execute.EnrollAndApproveExecute
import com.tgse.index.datasource.AwaitStatus
import com.tgse.index.datasource.Elasticsearch
import com.tgse.index.factory.MsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class Approve(
    private val enrollAndApproveExecute: EnrollAndApproveExecute,
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider,
    private val elasticsearch: Elasticsearch,
    private val awaitStatus: AwaitStatus,
    private val msgFactory: MsgFactory,
    @Value("\${group.approve.id}")
    private val approveGroupChatId: Long
) {
    private val logger = LoggerFactory.getLogger(Approve::class.java)

    init {
        subscribeUpdate()
        subscribeSubmitEnroll()
    }

    private fun subscribeUpdate() {
        watershedProvider.requestObservable.subscribe(
            { request ->
                try {
                    if (request !is WatershedProvider.BotApproveRequest) return@subscribe
                    // 回执
                    when {
                        request.update.callbackQuery() != null -> {
                            botProvider.sendTyping(request.chatId)
                            executeByButton(request)
                        }
                        awaitStatus.getAwaitStatus(request.chatId) != null -> {
                            botProvider.sendTyping(request.chatId)
                            enrollAndApproveExecute.executeByStatus(EnrollAndApproveExecute.Type.Approve, request)
                        }
                        request.update.message().text().startsWith("/") &&request.update.message().text().endsWith("@${botProvider.username}") -> {
                            botProvider.sendTyping(request.chatId)
                            executeByCommand(request)
                        }
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

    private fun subscribeSubmitEnroll() {
        elasticsearch.submitEnrollObservable.subscribe(
            { enrollId ->
                val msg = msgFactory.makeApproveMsg(approveGroupChatId, enrollId)
                botProvider.send(msg)
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("subscribeSubmitEnroll.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("subscribeSubmitEnroll.complete")
            }
        )
    }

    private fun executeByCommand(request: WatershedProvider.BotApproveRequest) {
        // 获取命令内容
        val cmd = request.update.message().text().replaceFirst("/", "").replace("@${botProvider.username}", "")
        // 回执
        val sendMessage = when (cmd) {
            "start", "enroll", "update", "setting", "help" -> msgFactory.makeReplyMsg(request.chatId, "disable")
            // todo: 修改，移除等操作
            "list" -> msgFactory.makeReplyMsg(request.chatId, "disable")
            "mine" -> msgFactory.makeReplyMsg(request.chatId, "only-private")
            else -> msgFactory.makeReplyMsg(request.chatId, "can-not-understand")
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeByButton(request: WatershedProvider.BotApproveRequest) {
        val answer = AnswerCallbackQuery(request.update.callbackQuery().id())
        botProvider.send(answer)

        val callbackData = request.update.callbackQuery().data()
        when {
            callbackData.startsWith("approve") || callbackData.startsWith("classification") -> {
                enrollAndApproveExecute.executeByEnrollButton(EnrollAndApproveExecute.Type.Approve,request)
            }
            callbackData.startsWith("page") -> {

            }
        }
    }


}