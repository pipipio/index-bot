package com.tgse.index.factory

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import com.pengrad.telegrambot.request.EditMessageText
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.datasource.RecordElastic
import com.tgse.index.datasource.Telegram
import com.tgse.index.provider.BotProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.math.RoundingMode
import java.text.DecimalFormat

@Component
class ListMsgFactory(
    private val botProvider: BotProvider,
    private val recordElastic: RecordElastic,
    @Value("\${secretary.list.size}")
    private val perPageSize: Int
) {

    fun makeListMsg(chatId: Long, keywords: String, pageIndex: Int): SendMessage? {
        val range = IntRange(((pageIndex - 1) * perPageSize), pageIndex * perPageSize)
        val (records, totalCount) = recordElastic.searchRecords(keywords, range.first, perPageSize)
        if (totalCount == 0L) return null
        val sb = StringBuffer()
        records.forEach {
            val item = generateRecordItem(it)
            sb.append(item)
        }
        val keyboard = makePageKeyboardMarkup(keywords, totalCount, pageIndex, range)
        return SendMessage(chatId, sb.toString())
            .parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeEditListMsg(chatId: Long, messageId: Int, keywords: String, pageIndex: Int): EditMessageText {
        val range = IntRange(((pageIndex - 1) * perPageSize), pageIndex * perPageSize)
        val (records, totalCount) = recordElastic.searchRecords(keywords, range.first, perPageSize)
        val sb = StringBuffer()
        records.forEach {
            val item = generateRecordItem(it)
            sb.append(item)
        }
        val keyboard = makePageKeyboardMarkup(keywords, totalCount, pageIndex, range)
        return EditMessageText(chatId, messageId, sb.toString())
            .parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    /**
     * 整理列表内容
     */
    private fun generateRecordItem(record: RecordElastic.Record): String {
        // 频道或群组图标
        val icon = when (record.type) {
            Telegram.TelegramModType.Group -> "\uD83D\uDC65"
            Telegram.TelegramModType.Channel -> "\uD83D\uDCE2"
            Telegram.TelegramModType.Bot -> "\uD83E\uDD16"
            else -> "❓"
        }
        // 成员数量
        val members = when (record.type) {
            Telegram.TelegramModType.Group -> getMemberUnit(record.members!!)
            Telegram.TelegramModType.Channel -> getMemberUnit(record.members!!)
            else -> ""
        }
        // 名称及地址
        val title = record.title.replace("<".toRegex(), "&lt;").replace(">".toRegex(), "&gt;")
        val display = "<a href='https://t.me/${botProvider.username}?start=${record.uuid}'>${title}</a>\n"
        // 最终
        return "$icon $members | $display"
    }

    private fun makePageKeyboardMarkup(
        keywords: String,
        totalCount: Long,
        pageIndex: Int,
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

    /**
     * 为数字增加单位
     */
    private fun getMemberUnit(count: Long): String {
        var size = count.toDouble()
        // 数值过大增加单位
        val unit = when {
            count > 1000000 -> {
                size = count / 1000000.0
                "M"
            }
            count > 1000 -> {
                size = count / 1000.0
                "K"
            }
            else -> ""
        }
        // 保留1位小数
        val formatter = DecimalFormat()
        formatter.maximumFractionDigits = 1
        formatter.groupingSize = 0
        formatter.roundingMode = RoundingMode.FLOOR
        return formatter.format(size) + unit
    }

}