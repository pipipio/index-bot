package com.scomarlf.index.provider

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.request.*
import com.pengrad.telegrambot.response.BaseResponse
import com.pengrad.telegrambot.response.SendResponse
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BotProvider(
    @Value("\${bot.token}")
    private val token: String,
    @Value("\${bot.creator}")
    private val creator: Long
) {
    private val bot = TelegramBot(token)
    private val updateSubject = BehaviorSubject.create<Update>()
    val updateObservable: Observable<Update> = updateSubject.distinct()

    init {
        handleUpdate()
        println("Bot ready.")
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

    fun send(action: SendChatAction): BaseResponse {
        return bot.execute(action)
    }

    fun send(answer: AnswerCallbackQuery): BaseResponse {
        return bot.execute(answer)
    }

    fun send(message: EditMessageReplyMarkup): BaseResponse {
        return bot.execute(message)
    }

    fun sendErrorMessage(error: Throwable) {
        val errorMessage = SendMessage(creator, error.stackTraceToString())
        bot.execute(errorMessage)
    }
}