package com.tgse.index.area.msgFactory

import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.MismatchException
import com.tgse.index.datasource.*
import com.tgse.index.provider.BotProvider
import org.springframework.stereotype.Component

@Component
class NormalMsgFactory(
    private val type: Type,
    override val reply: Reply,
    override val botProvider: BotProvider
) : BaseMsgFactory(reply, botProvider) {

    fun makeClearMarkupMsg(chatId: Long, messageId: Int): EditMessageReplyMarkup {
        return EditMessageReplyMarkup(chatId, messageId).replyMarkup(InlineKeyboardMarkup())
    }

    fun makeStatisticsDailyReplyMsg(
        chatId: Long,
        dailyIncreaseOfUser: Long,
        dailyActiveOfUser: Long,
        countOfUser: Long,
        countOfRecord: Long
    ): SendMessage {
        return SendMessage(
            chatId,
            reply.message["statistics-daily"]!!
                .replace("\\{dailyIncreaseOfUser\\}".toRegex(), dailyIncreaseOfUser.toString())
                .replace("\\{dailyActiveOfUser\\}".toRegex(), dailyActiveOfUser.toString())
                .replace("\\{countOfUser\\}".toRegex(), countOfUser.toString())
                .replace("\\{countOfRecord\\}".toRegex(), countOfRecord.toString())
        )
    }

    fun makeListReplyMsg(chatId: Long): SendMessage {
        val keyboard = makeReplyKeyboardMarkup()
        return SendMessage(chatId, reply.message["list"]!!).replyMarkup(keyboard)
    }

    fun makeBlacklistJoinedReplyMsg(
        chatId: Long,
        replyType: String,
        manager: String,
        black: Blacklist.Black
    ): SendMessage {
        return SendMessage(
            chatId,
            reply.message[replyType]!!
                .replace("\\{manager\\}".toRegex(), manager)
                .replace("\\{black\\}".toRegex(), black.displayName)
        )
    }

    fun makeBlacklistExistReplyMsg(chatId: Long, replyType: String, type: String): SendMessage {
        return SendMessage(
            chatId,
            reply.message[replyType]!!.replace("\\{type\\}".toRegex(), type)
        )
    }

    fun makeRemoveRecordReplyMsg(chatId: Long, manager: String, recordTitle: String): SendMessage {
        return SendMessage(
            chatId,
            reply.message["remove-record-manager"]!!
                .replace("\\{manager\\}".toRegex(), manager)
                .replace("\\{record\\}".toRegex(), recordTitle)
        )
    }

    fun makeRemoveRecordReplyMsg(chatId: Long, recordTitle: String): SendMessage {
        return SendMessage(
            chatId,
            reply.message["remove-record-user"]!!
                .replace("\\{record\\}".toRegex(), recordTitle)
        )
    }

    fun makeExceptionMsg(chatId: Long, e: Exception): SendMessage {
        return when (e) {
            is MismatchException -> SendMessage(chatId, e.message)
            else -> SendMessage(chatId, "未知错误")
        }
    }

    private fun makeReplyKeyboardMarkup(): ReplyKeyboardMarkup {
        // 每行countInRow数量个按钮
        val countInRow = 3
        // 将多个类型按照countInRow拆分为多行
        var counter = 0
        val rows = mutableListOf<Array<String>>()
        while (counter < type.types.size) {
            var endOfIndex = counter + countInRow
            endOfIndex = if (endOfIndex <= type.types.size) endOfIndex else type.types.size
            val row = type.types.copyOfRange(counter, endOfIndex)
            counter += countInRow
            rows.add(row)
        }
        // 制作键盘
        val keyboard = ReplyKeyboardMarkup(*rows.toTypedArray())
        keyboard.oneTimeKeyboard(false)
        keyboard.resizeKeyboard(true)
        keyboard.selective(true)
        return keyboard
    }
}