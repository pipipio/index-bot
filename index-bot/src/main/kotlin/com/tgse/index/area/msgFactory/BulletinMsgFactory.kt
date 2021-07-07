package com.tgse.index.area.msgFactory

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.domain.service.RecordService
import com.tgse.index.domain.service.ReplyService
import com.tgse.index.infrastructure.provider.BotProvider
import org.springframework.stereotype.Component

@Component
class BulletinMsgFactory(
    override val replyService: ReplyService,
    override val botProvider: BotProvider
) : BaseMsgFactory(replyService, botProvider) {

    fun makeBulletinMsg(chatId: Long, record: RecordService.Record): SendMessage {
        val detail = makeRecordDetail(record)
        val keyboard = makePointKeyboardMarkup(record.uuid)
        return SendMessage(chatId, detail).parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeBulletinMsg(chatId: Long, messageId: Int, record: RecordService.Record): EditMessageText {
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