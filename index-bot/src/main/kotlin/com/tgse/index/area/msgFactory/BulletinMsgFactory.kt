package com.tgse.index.area.msgFactory

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.datasource.*
import com.tgse.index.provider.BotProvider
import org.springframework.stereotype.Component

@Component
class BulletinMsgFactory(
    override val reply: Reply,
    override val botProvider: BotProvider
) : BaseMsgFactory(reply, botProvider) {

    fun makeBulletinMsg(chatId: Long, record: RecordElastic.Record): SendMessage {
        val detail = makeRecordDetail(record)
        val keyboard = makePointKeyboardMarkup(record.uuid)
        return SendMessage(chatId, detail).parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeBulletinMsg(chatId: Long, messageId: Int, record: RecordElastic.Record): EditMessageText {
        val detail = makeRecordDetail(record)
        val keyboard = makePointKeyboardMarkup(record.uuid)
        return EditMessageText(chatId, messageId, detail).parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    private fun makePointKeyboardMarkup(enrollUUID: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("查询").url("https://t.me/${botProvider.username}?start=$enrollUUID")
            )
        )
    }

}