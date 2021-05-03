package com.tgse.index.bot

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.bot.execute.BlacklistExecute
import com.tgse.index.bot.execute.RecordExecute
import com.tgse.index.datasource.AwaitStatus
import com.tgse.index.datasource.EnrollElastic
import com.tgse.index.datasource.RecordElastic
import com.tgse.index.factory.MsgFactory
import com.tgse.index.nick
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class Approve(
    private val recordExecute: RecordExecute,
    private val blacklistExecute: BlacklistExecute,
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider,
    private val enrollElastic: EnrollElastic,
    private val recordElastic: RecordElastic,
    private val awaitStatus: AwaitStatus,
    private val msgFactory: MsgFactory,
    @Value("\${group.approve.id}")
    private val approveGroupChatId: Long
) {
    private val logger = LoggerFactory.getLogger(Approve::class.java)

    init {
        subscribeUpdate()
        subscribeFeedback()
        subscribeSubmitEnroll()
        subscribeDeleteRecord()
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
                            recordExecute.executeByStatus(RecordExecute.Type.Approve, request)
                        }
                        request.update.message().text().startsWith("/") && request.update.message().text()
                            .endsWith("@${botProvider.username}") -> {
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
                logger.error("Approve.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Approve.complete")
            }
        )
    }

    private fun subscribeFeedback() {
        watershedProvider.feedbackObservable.subscribe(
            { request ->
                try {
                    val recordMsg = msgFactory.makeFeedbackMsg(approveGroupChatId,request.first)
                    botProvider.send(recordMsg)
                    val feedbackMsg = SendMessage(approveGroupChatId,"反馈：\n${request.second}")
                    botProvider.send(feedbackMsg)
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Approve.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Approve.complete")
            }
        )
    }

    private fun subscribeSubmitEnroll() {
        enrollElastic.submitEnrollObservable.subscribe(
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

    private fun subscribeDeleteRecord() {
        recordElastic.deleteRecordObservable.subscribe(
            { next ->
                try {
                    val msg = msgFactory.makeRemoveRecordReplyMsg(approveGroupChatId, next.second.nick(), next.first.title)
                    botProvider.send(msg)
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Approve.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Approve.complete")
            }
        )
    }

    private fun executeByCommand(request: WatershedProvider.BotApproveRequest) {
        // 获取命令内容
        val cmd = request.update.message().text().replaceFirst("/", "").replace("@${botProvider.username}", "")
        // 回执
        val sendMessage = when (cmd) {
            "start", "enroll", "update", "setting", "help" -> msgFactory.makeReplyMsg(request.chatId, "disable")
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
                recordExecute.executeByEnrollButton(RecordExecute.Type.Approve, request)
            }
            callbackData.startsWith("blacklist") -> {
                blacklistExecute.executeByBlacklistButton(request)
            }
            callbackData.startsWith("remove") -> {
                val manager = request.update.callbackQuery().from()
                val recordUUID = callbackData.replace("remove:", "")
                recordElastic.deleteRecord(recordUUID, manager)
            }
        }
    }


}