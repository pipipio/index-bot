package com.tgse.index.msgFactory

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.datasource.RecordElastic
import com.tgse.index.datasource.Reply
import com.tgse.index.provider.BotProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class ListMsgFactory(
    override val botProvider: BotProvider,
    override val reply: Reply,
    private val recordElastic: RecordElastic,
    @Value("\${secretary.list.size}")
    private val perPageSize: Int
) : BaseMsgFactory(reply, botProvider) {

    fun makeListFirstPageMsg(chatId: Long, keywords: String, pageIndex: Int): SendMessage? {
        val range = IntRange(((pageIndex - 1) * perPageSize), pageIndex * perPageSize)
        val (records, totalCount) = recordElastic.searchRecords(keywords, range.first, perPageSize)
        if (totalCount == 0L) return null
        val sb = StringBuffer()
        records.forEach {
            val item = generateRecordItem(it)
            sb.append(item)
        }
        val keyboard = makeListPageKeyboardMarkup(keywords, totalCount, pageIndex, perPageSize, range)
        return SendMessage(chatId, sb.toString())
            .parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeListNextPageMsg(chatId: Long, messageId: Int, keywords: String, pageIndex: Int): EditMessageText {
        val range = IntRange(((pageIndex - 1) * perPageSize), pageIndex * perPageSize)
        val (records, totalCount) = recordElastic.searchRecords(keywords, range.first, perPageSize)
        val sb = StringBuffer()
        records.forEach {
            val item = generateRecordItem(it)
            sb.append(item)
        }
        val keyboard = makeListPageKeyboardMarkup(keywords, totalCount, pageIndex, perPageSize, range)
        return EditMessageText(chatId, messageId, sb.toString())
            .parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    private fun makeListPageKeyboardMarkup(
        keywords: String,
        totalCount: Long,
        pageIndex: Int,
        perPageSize: Int,
        range: IntRange
    ): InlineKeyboardMarkup {
        return when {
            totalCount > perPageSize && range.first == 0 ->
                InlineKeyboardMarkup(
                    arrayOf(
                        InlineKeyboardButton("下一页").callbackData("page:$keywords&${pageIndex + 1}"),
                    )
                )
            totalCount > perPageSize && range.first != 0 && range.last < totalCount ->
                InlineKeyboardMarkup(
                    arrayOf(
                        InlineKeyboardButton("上一页").callbackData("page:$keywords&${pageIndex - 1}"),
                        InlineKeyboardButton("下一页").callbackData("page:$keywords&${pageIndex + 1}"),
                    )
                )
            totalCount > perPageSize && range.last >= totalCount ->
                InlineKeyboardMarkup(
                    arrayOf(
                        InlineKeyboardButton("上一页").callbackData("page:$keywords&${pageIndex - 1}"),
                    )
                )
            else -> InlineKeyboardMarkup()
        }
    }

}