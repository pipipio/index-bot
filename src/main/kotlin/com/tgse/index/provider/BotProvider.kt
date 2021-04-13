package com.tgse.index.provider

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.BotCommand
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ChatAction
import com.pengrad.telegrambot.request.*
import com.pengrad.telegrambot.response.*
import com.tgse.index.SetCommandException
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.InetSocketAddress

@Component
class BotProvider(
    @Value("\${bot.token}")
    private val token: String,
    @Value("\${bot.creator}")
    private val creator: Long,
    @Value("\${proxy.use}")
    private val isUseProxy: Boolean,
    @Value("\${proxy.type}")
    private val proxyType: String,
    @Value("\${proxy.ip}")
    private val proxyIp: String,
    @Value("\${proxy.port}")
    private val proxyPort: Int
) {
    private val bot: TelegramBot by lazy {
        if (isUseProxy) {
            val socketAddress = InetSocketAddress(proxyIp, proxyPort)
            val proxy = java.net.Proxy(java.net.Proxy.Type.valueOf(proxyType), socketAddress)
            val okHttpClient = OkHttpClient().newBuilder().proxy(proxy).build()
            TelegramBot.Builder(token).okHttpClient(okHttpClient).build()
        } else {
            TelegramBot(token)
        }
    }

    private val updateSubject = BehaviorSubject.create<Update>()
    val updateObservable: Observable<Update> = updateSubject.distinct()
    val username: String by lazy {
        val request = GetMe()
        val response = bot.execute(request)
        response.user().username()
    }

    init {
        setCommands()
        handleUpdate()
        println("Bot ready.")
    }

    private fun setCommands() {
        try {
            val setCommands = SetMyCommands(
                BotCommand("start", "开 始"),
                BotCommand("enroll", "申请收录"),
                BotCommand("update", "修改收录信息"),
                BotCommand("list", "收录列表"),
                BotCommand("mine", "我提交的"),
                BotCommand("setting", "设 置"),
                BotCommand("help", "帮 助"),
            )
            val setResponse = bot.execute(setCommands)
            if (!setResponse.isOk)
                throw SetCommandException(setResponse.description())
        } catch (e: Throwable) {
            sendErrorMessage(e)
        }
    }

    private fun handleUpdate() {
        bot.setUpdatesListener { updates ->
            updates.forEach { update ->
                updateSubject.onNext(update)
            }
            UpdatesListener.CONFIRMED_UPDATES_ALL
        }
    }

    fun send(message: SendMessage): SendResponse {
        return bot.execute(message)
    }

    fun sendDeleteMessage(chatId: Long, messageId: Int): BaseResponse {
        val deleteMessage = DeleteMessage(chatId, messageId)
        return bot.execute(deleteMessage)
    }


    fun send(answer: AnswerCallbackQuery): BaseResponse {
        return bot.execute(answer)
    }

    fun send(message: EditMessageReplyMarkup): BaseResponse {
        return bot.execute(message)
    }

    fun send(action: GetChat): GetChatResponse {
        return bot.execute(action)
    }

    fun send(action: GetChatMembersCount): GetChatMembersCountResponse {
        return bot.execute(action)
    }

    fun send(action: GetChatAdministrators): GetChatAdministratorsResponse {
        return bot.execute(action)
    }

    fun sendTyping(chatId: Long) {
        val chatAction = SendChatAction(chatId, ChatAction.typing)
        send(chatAction)
    }

    private fun send(action: SendChatAction): BaseResponse {
        return bot.execute(action)
    }

    fun sendErrorMessage(error: Throwable) {
        val msgContent = "Error:\n" + (error.message ?: error.stackTrace.copyOfRange(0, 4).joinToString("\n"))
        val errorMessage = SendMessage(creator, msgContent)
        bot.execute(errorMessage)
    }
}