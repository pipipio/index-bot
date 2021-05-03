package com.tgse.index.factory

import com.pengrad.telegrambot.model.User
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.request.EditMessageReplyMarkup
import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.MismatchException
import com.tgse.index.datasource.*
import com.tgse.index.nick
import com.tgse.index.provider.BotProvider
import org.springframework.stereotype.Component

@Component
class MsgFactory(
    private val reply: Reply,
    private val type: Type,
    private val enrollElastic: EnrollElastic,
    private val recordElastic: RecordElastic,
    private val botProvider: BotProvider
) {

    fun makeEnrollMsg(chatId: Long, enrollId: String): SendMessage {
        val enroll = enrollElastic.getEnroll(enrollId)!!
        val detail = makeRecordDetail(enroll)
        val keyboard = makeEnrollKeyboardMarkup(enrollId)
        return SendMessage(chatId, detail)
            .parseMode(ParseMode.HTML)
            .disableWebPagePreview(true)
            .replyMarkup(keyboard)
    }

    fun makeEnrollChangeClassificationMsg(chatId: Long, enrollId: String): SendMessage {
        val enroll = enrollElastic.getEnroll(enrollId)!!
        val detail = makeRecordDetail(enroll)
        val keyboard = makeInlineKeyboardMarkup(enrollId)
        val msg = SendMessage(chatId, detail)
        return msg.parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeApproveMsg(chatId: Long, enrollId: String): SendMessage {
        val enroll = enrollElastic.getEnroll(enrollId)!!
        val detail = makeApproveRecordDetail(enroll)
        val keyboard = makeApproveKeyboardMarkup(enrollId)
        return SendMessage(chatId, detail)
            .replyMarkup(keyboard)
            .parseMode(ParseMode.HTML)
            .disableWebPagePreview(true)
    }

    fun makeApproveChangeClassificationMsg(chatId: Long, enrollId: String): SendMessage {
        val enroll = enrollElastic.getEnroll(enrollId)!!
        val detail = makeApproveRecordDetail(enroll)
        val keyboard = makeInlineKeyboardMarkup(enrollId)
        val msg = SendMessage(chatId, detail)
        return msg.parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeApproveResultMsg(chatId: Long, enrollId: String, manager: User, isPassed: Boolean): SendMessage {
        val enroll = enrollElastic.getEnroll(enrollId)!!
        val detail = makeApproveResultDetail(enroll, manager, isPassed)
        val keyboard = makeJoinBlacklistKeyboardMarkup(enroll)
        val msg = SendMessage(chatId, detail)
        return if (isPassed) msg.parseMode(ParseMode.HTML).disableWebPagePreview(true)
        else msg.parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeApproveResultMsg(chatId: Long, enrollId: String, isPassed: Boolean): SendMessage {
        val enroll = enrollElastic.getEnroll(enrollId)!!
        val detail = makeApproveResultDetail(enroll, isPassed)
        val msg = SendMessage(chatId, detail)
        return msg.parseMode(ParseMode.HTML).disableWebPagePreview(true)
    }

    fun makeBulletinMsg(chatId: Long, record: RecordElastic.Record): SendMessage {
        val detail = makeRecordDetail(record)
        val keyboard = makePointKeyboardMarkup(record.uuid)
        return SendMessage(chatId, detail).parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeRecordMsg(chatId: Long, recordUUID: String): SendMessage {
        val record = recordElastic.getRecord(recordUUID)!!
        val detail = makeRecordDetail(record)
        val keyboard = makeFeedbackKeyboardMarkup(recordUUID)
        return SendMessage(chatId, detail).parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeFeedbackMsg(chatId: Long, recordUUID: String): SendMessage {
        val record = recordElastic.getRecord(recordUUID)!!
        val detail = makeRecordDetail(record)
        val keyboard = makeManageKeyboardMarkup(recordUUID)
        return SendMessage(chatId, detail).parseMode(ParseMode.HTML).disableWebPagePreview(true).replyMarkup(keyboard)
    }

    fun makeClearMarkupMsg(chatId: Long, messageId: Int): EditMessageReplyMarkup {
        return EditMessageReplyMarkup(chatId, messageId).replyMarkup(InlineKeyboardMarkup())
    }

    fun makeReplyMsg(chatId: Long, replyType: String): SendMessage {
        return SendMessage(
            chatId,
            reply.message[replyType]!!.replace("\\{bot.username\\}".toRegex(), botProvider.username)
        )
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

    fun makeBlacklistJoinedReplyMsg(chatId: Long, replyType: String, manager: String, black: Blacklist.Black): SendMessage {
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
            reply.message["remove-record"]!!
                .replace("\\{manager\\}".toRegex(), manager)
                .replace("\\{record\\}".toRegex(), recordTitle)
        )
    }

    fun makeListReplyMsg(chatId: Long): SendMessage {
        val keyboard = makeReplyKeyboardMarkup()
        return SendMessage(chatId, "list").replyMarkup(keyboard)
    }

    fun makeExceptionMsg(chatId: Long, e: Exception): SendMessage {
        return when (e) {
            is MismatchException -> SendMessage(chatId, e.message)
            else -> SendMessage(chatId, "未知错误")
        }
    }

    private fun makeRecordDetail(record: RecordElastic.Record): String {
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

    private fun makeRecordDetail(enroll: EnrollElastic.Enroll): String {
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

    private fun makeApproveRecordDetail(enroll: EnrollElastic.Enroll): String {
        return makeRecordDetail(enroll) + "\n<b>提交者</b>： ${enroll.createUserNick}\n"
    }

    private fun makeApproveResultDetail(enroll: EnrollElastic.Enroll, checker: User, isPassed: Boolean): String {
        val result = if (isPassed) "通过" else "未通过"
        return makeRecordDetail(enroll) +
                "\n<b>提交者</b>： ${enroll.createUserNick}" +
                "\n<b>审核者</b>： ${checker.nick()}" +
                "\n<b>审核结果</b>： $result\n"
    }

    private fun makeApproveResultDetail(enroll: EnrollElastic.Enroll, isPassed: Boolean): String {
        val result = if (isPassed) "通过" else "未通过"
        return makeRecordDetail(enroll) +
                "\n<b>审核结果</b>： $result\n"
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

    private fun makeInlineKeyboardMarkup(id: String): InlineKeyboardMarkup {
        // 每行countInRow数量个按钮
        val countInRow = 3
        // 将多个类型按照countInRow拆分为多行
        var counter = 0
        val buttonLines = mutableListOf<Array<InlineKeyboardButton>>()
        while (counter < type.types.size) {
            var endOfIndex = counter + countInRow
            endOfIndex = if (endOfIndex <= type.types.size) endOfIndex else type.types.size
            val row = type.types.copyOfRange(counter, endOfIndex)
            val buttons = row.map {
                InlineKeyboardButton(it).callbackData("classification:$it&$id")
            }.toTypedArray()
            buttonLines.add(buttons)
            counter += countInRow
        }
        return InlineKeyboardMarkup(*buttonLines.toTypedArray())
    }

    private fun makePointKeyboardMarkup(enrollUUID: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                    InlineKeyboardButton("查询").url("https://t.me/${botProvider.username}?start=$enrollUUID")
            )
        )
    }

    private fun makeFeedbackKeyboardMarkup(recordUUID: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("反馈").callbackData("feedback:$recordUUID")
            )
        )
    }

    private fun makeManageKeyboardMarkup(recordUUID: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("移除").callbackData("remove:$recordUUID")
            )
        )
    }

    private fun makeJoinBlacklistKeyboardMarkup(enroll: EnrollElastic.Enroll): InlineKeyboardMarkup {
        val type = when (enroll.type) {
            Telegram.TelegramModType.Channel -> "频道"
            Telegram.TelegramModType.Group -> "群组"
            Telegram.TelegramModType.Bot -> "机器人"
            Telegram.TelegramModType.Person -> throw RuntimeException("收录对象为用户")
        }
        return InlineKeyboardMarkup(
            arrayOf(
                run {
                    val callbackData =
                        if (enroll.chatId != null) "blacklist:join&${Blacklist.BlackType.Record}&${enroll.uuid}"
                        else "blacklist:join&${Blacklist.BlackType.Record}&${enroll.uuid}"
                    InlineKeyboardButton("将${type}加入黑名单").callbackData(callbackData)
                }
            ),
            arrayOf(
                run {
                    val callbackData = "blacklist:join&${Blacklist.BlackType.User}&${enroll.uuid}"
                    InlineKeyboardButton("将提交者加入黑名单").callbackData(callbackData)
                }
            )
        )
    }

    private fun makeEnrollKeyboardMarkup(id: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("✍编辑标题").callbackData("enroll:title&$id"),
                InlineKeyboardButton("✍编辑简介").callbackData("enroll:about&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✍编辑标签").callbackData("enroll:tags&$id"),
                InlineKeyboardButton("✍编辑分类").callbackData("enroll:classification&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✅提交").callbackData("enroll:submit&$id"),
                InlineKeyboardButton("❎取消").callbackData("enroll:cancel&$id"),
            )
        )
    }

    private fun makeApproveKeyboardMarkup(id: String): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("✍编辑标题").callbackData("approve:title&$id"),
                InlineKeyboardButton("✍编辑简介").callbackData("approve:about&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✍编辑标签").callbackData("approve:tags&$id"),
                InlineKeyboardButton("✍编辑分类").callbackData("approve:classification&$id"),
            ),
            arrayOf(
                InlineKeyboardButton("✅通过").callbackData("approve:pass&$id"),
                InlineKeyboardButton("❎不通过").callbackData("approve:fail&$id"),
            )
        )
    }
}