package com.tgse.index.bot

import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.*
import com.tgse.index.bot.execute.BlacklistExecute
import com.tgse.index.bot.execute.RecordExecute
import com.tgse.index.datasource.*
import com.tgse.index.factory.ListMsgFactory
import com.tgse.index.factory.MsgFactory
import com.tgse.index.nick
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
    private val blacklistExecute: BlacklistExecute,
    private val recordExecute: RecordExecute,
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider,
    private val msgFactory: MsgFactory,
    private val listMsgFactory: ListMsgFactory,
    private val enrollElastic: EnrollElastic,
    private val userElastic: UserElastic,
    private val blacklist: Blacklist,
    private val telegram: Telegram,
    private val awaitStatus: AwaitStatus
) {

    private val logger = LoggerFactory.getLogger(Private::class.java)

    init {
        subscribeUpdate()
        subscribeApprove()
    }

    private fun subscribeUpdate() {
        watershedProvider.requestObservable.subscribe(
            { request ->
                try {
                    if (request !is BotPrivateRequest) return@subscribe
                    // 输入状态
                    botProvider.sendTyping(request.chatId)
                    // 回执
                    when {
                        request.update.callbackQuery() != null -> executeByButton(request)
                        awaitStatus.getAwaitStatus(request.chatId) != null -> {
                            val callbackData = awaitStatus.getAwaitStatus(request.chatId)!!.callbackData
                            if (callbackData.startsWith("approve") || callbackData.startsWith("enroll"))
                                recordExecute.executeByStatus(RecordExecute.Type.Enroll, request)
                            else
                                executeByStatus(RecordExecute.Type.Enroll, request)
                        }
                        request.update.message().text().startsWith("/") -> executeByCommand(request)
                        request.update.message().text().startsWith("@") -> executeByEnroll(request)
                        request.update.message().text().startsWith("https://t.me/") -> executeByEnroll(request)
                        else -> executeByText(request)
                    }
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }finally {
                    // 记录日活用户
                    val user = when {
                        request.update.message() != null -> request.update.message().from()
                        else -> request.update.callbackQuery().message().from()
                    }
                    userElastic.footprint(user)
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Private.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Private.complete")
            }
        )
    }

    private fun subscribeApprove() {
        enrollElastic.submitApproveObservable.subscribe(
            { next ->
                try {
                    val (enrollUUID, isPassed) = next
                    val enroll = enrollElastic.getEnroll(enrollUUID)!!
                    val msg = msgFactory.makeApproveResultMsg(enroll.createUser, enroll.uuid, isPassed)
                    botProvider.send(msg)
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Private.approve.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Private.approve.complete")
            }
        )
    }

    private fun executeByText(request: BotPrivateRequest) {
        val keywords = request.update.message().text()
        val msg =
            listMsgFactory.makeListMsg(request.chatId, keywords, 1) ?: msgFactory.makeReplyMsg(    request.chatId, "nothing"   )
        botProvider.send(msg)
    }

    private fun executeByEnroll(request: BotPrivateRequest) {
        // 人员黑名单检测
        val userBlack = blacklist.get(request.chatId)
        if (userBlack != null) {
            val user = request.update.message().from()
            val telegramPerson = Telegram.TelegramPerson(request.chatId, user.username(), user.nick(), null)
            blacklistExecute.notify(request.chatId, telegramPerson)
            return
        }
        // 获取收录内容
        val username = request.update.message().text().replaceFirst("@", "").replaceFirst("https://t.me/", "")
        val telegramMod = telegram.getTelegramModFromWeb(username)
        // 收录对象黑名单检测
        val recordBlack = blacklist.get(username)
        if (recordBlack != null && telegramMod != null) {
            blacklistExecute.notify(request.chatId, telegramMod)
            return
        }
        // 回执
        val sendMessage = when (telegramMod) {
            is Telegram.TelegramGroup -> {
                msgFactory.makeReplyMsg(request.chatId, "enroll-need-join-group")
            }
            is Telegram.TelegramChannel -> {
                val enroll = EnrollElastic.Enroll(
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
                    request.update.message().from().nick()
                )
                val createEnroll = enrollElastic.addEnroll(enroll)
                if (!createEnroll) return
                msgFactory.makeEnrollMsg(request.chatId, enroll.uuid)
            }
            is Telegram.TelegramBot -> {
                val enroll = EnrollElastic.Enroll(
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
                    request.update.message().from().nick()
                )
                val createEnroll = enrollElastic.addEnroll(enroll)
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
        val sendMessage = when {
            cmd == "start" -> msgFactory.makeReplyMsg(request.chatId, "start")
            cmd.startsWith("start ") -> executeBySuperCommand(request)
            cmd == "enroll" -> msgFactory.makeReplyMsg(request.chatId, cmd)
            cmd == "update" || cmd == "mine" -> msgFactory.makeReplyMsg(request.chatId, "await")
            cmd == "list" -> msgFactory.makeListReplyMsg(request.chatId)
            cmd == "setting" -> msgFactory.makeReplyMsg(request.chatId, "only-group")
            cmd == "help" -> msgFactory.makeReplyMsg(request.chatId, "help-private")
            else -> msgFactory.makeReplyMsg(request.chatId, "can-not-understand")
        }
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.send(sendMessage)
    }

    private fun executeBySuperCommand(request: BotPrivateRequest): SendMessage {
        val recordUUID = request.update.message().text().replaceFirst("/start ", "")
        return msgFactory.makeRecordMsg(request.chatId, recordUUID)
    }

    private fun executeByButton(request: BotPrivateRequest) {
        val answer = AnswerCallbackQuery(request.update.callbackQuery().id())
        botProvider.send(answer)

        val callbackData = request.update.callbackQuery().data()
        when {
            callbackData.startsWith("enroll") || callbackData.startsWith("classification") -> {
                recordExecute.executeByEnrollButton(RecordExecute.Type.Enroll, request)
            }
            callbackData.startsWith("page") -> {
                val callback = callbackData.replace("page:", "").split("&")
                val keywords = callback[0]
                val pageIndex = callback[1].toInt()
                val msg = listMsgFactory.makeEditListMsg(request.chatId,request.messageId!!, keywords, pageIndex)
                botProvider.send(msg)
            }
            callbackData.startsWith("feedback") -> {
                awaitStatus.setAwaitStatus(request.chatId, AwaitStatus.Await(request.messageId!!, callbackData))
                val msg = msgFactory.makeReplyMsg(request.chatId, "feedback-start")
                botProvider.send(msg)
            }
        }
    }

    fun executeByStatus(type: RecordExecute.Type, request: BotRequest) {
        val statusCallbackData = awaitStatus.getAwaitStatus(request.chatId!!)!!.callbackData
        when {
            statusCallbackData.startsWith("feedback:") -> {
                val recordUUID = statusCallbackData.replace("feedback:", "")
                val content = request.update.message().text()
                val feedback = Pair(recordUUID,content)
                watershedProvider.feedbackSubject.onNext(feedback)
                // 清除状态
                awaitStatus.clearAwaitStatus(request.chatId!!)
                val msg = msgFactory.makeReplyMsg(request.chatId!!, "feedback-finish")
                botProvider.send(msg)
            }
        }
    }
}