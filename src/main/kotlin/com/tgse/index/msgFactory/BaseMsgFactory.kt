package com.tgse.index.msgFactory

import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.datasource.*
import com.tgse.index.provider.BotProvider
import java.math.RoundingMode
import java.text.DecimalFormat

abstract class BaseMsgFactory(
    protected open val reply: Reply,
    protected open val botProvider: BotProvider
) {

    fun makeReplyMsg(chatId: Long, replyType: String): SendMessage {
        return SendMessage(
            chatId,
            reply.message[replyType]!!.replace("\\{bot.username\\}".toRegex(), botProvider.username)
        )
    }

    protected fun makeRecordDetail(record: RecordElastic.Record): String {
        val link = if (record.username != null) "https://t.me/${record.username}" else record.link
        val detailSB = StringBuffer()
        detailSB.append("<b>标题</b>： <a href=\"$link\">${record.title}</a>\n")
        detailSB.append("<b>标签</b>： ${if (record.tags == null) "暂无" else record.tags.joinToString(" ")}\n")
        detailSB.append("<b>分类</b>： ${record.classification ?: "暂无"}\n")
        detailSB.append("<b>简介</b>：\n")
        val description = if (record.description == null) ""
        else record.description.replace("<", "&lt;").replace(">", "&gt;") + "\n"
        detailSB.append(description)
        return detailSB.toString()
    }

    protected fun makeRecordDetail(enroll: EnrollElastic.Enroll): String {
        val link = if (enroll.username != null) "https://t.me/${enroll.username}" else enroll.link
        val detailSB = StringBuffer()
        detailSB.append("<b>标题</b>： <a href=\"$link\">${enroll.title}</a>\n")
        detailSB.append("<b>标签</b>： ${if (enroll.tags == null) "暂无" else enroll.tags.joinToString(" ")}\n")
        detailSB.append("<b>分类</b>： ${enroll.classification ?: "暂无"}\n")
        detailSB.append("<b>简介</b>：\n")
        val description = if (enroll.description == null) ""
        else enroll.description.replace("<", "&lt;").replace(">", "&gt;") + "\n"
        detailSB.append(description)
        return detailSB.toString()
    }

    /**
     * 整理列表内容
     */
    protected fun generateRecordItem(record: RecordElastic.Record): String {
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

    /**
     * 整理列表内容
     */
    protected fun generateEnrollItem(enroll: EnrollElastic.Enroll): String {
        // 频道或群组图标
        val icon = "⏳"
        // 成员数量
        val members = when (enroll.type) {
            Telegram.TelegramModType.Group -> getMemberUnit(enroll.members!!)
            Telegram.TelegramModType.Channel -> getMemberUnit(enroll.members!!)
            else -> ""
        }
        // 名称及地址
        val title = enroll.title.replace("<".toRegex(), "&lt;").replace(">".toRegex(), "&gt;")
        val display = "${title}\n"
        // 最终
        return "$icon $members | $display"
    }

    /**
     * 为数字增加单位
     */
    protected fun getMemberUnit(count: Long): String {
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