package com.scomarlf.index.provider

import com.pengrad.telegrambot.TelegramBot
import com.pengrad.telegrambot.UpdatesListener
import com.pengrad.telegrambot.model.Update
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup
import com.pengrad.telegrambot.model.request.InlineKeyboardButton
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup
import com.pengrad.telegrambot.request.*
import com.pengrad.telegrambot.response.BaseResponse
import com.pengrad.telegrambot.response.SendResponse
import com.scomarlf.index.datasource.Elasticsearch
import com.scomarlf.index.datasource.Telegram
import com.scomarlf.index.datasource.Type
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import org.elasticsearch.action.delete.DeleteResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class BotProvider(
    @Value("\${bot.token}")
    private val token: String,
    @Value("\${bot.creator}")
    private val creator: Long,
    private val type: Type
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

    fun send(message: DeleteMessage): BaseResponse {
        return bot.execute(message)
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

    fun makeRecordDetail(mod: Telegram.TelegramMod): String {
        val link = "https://t.me/${mod.id}"
        val detailSB = StringBuffer()
        detailSB.append("<b>标题</b>： <a href=\"$link\">${mod.title}</a>\n")
        detailSB.append("<b>标签</b>： 暂无\n")
        detailSB.append("<b>分类</b>： 暂无\n")
        detailSB.append("<b>简介</b>：\n")
        detailSB.append(mod.about.replace("<", "&lt;").replace(">", "&gt;"))
        return detailSB.toString()
    }

    fun makeRecordDetail(enroll: Elasticsearch.Enroll): String {
        val link = "https://t.me/${enroll.code}"
        val detailSB = StringBuffer()
        detailSB.append("<b>标题</b>： <a href=\"$link\">${enroll.title}</a>\n")
        detailSB.append("<b>标签</b>： ${if (enroll.tags == null) "暂无" else enroll.tags.joinToString(" ")}\n")
        detailSB.append("<b>分类</b>： ${enroll.classification ?: "暂无"}\n")
        detailSB.append("<b>简介</b>：\n")
        detailSB.append(enroll.about.replace("<", "&lt;").replace(">", "&gt;"))
        return detailSB.toString()
    }

    fun makeReplyKeyboardMarkup(): ReplyKeyboardMarkup {
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

    fun makeInlineKeyboardMarkup(id: String): InlineKeyboardMarkup {
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
        val keyboard = InlineKeyboardMarkup(*buttonLines.toTypedArray())
        return keyboard
    }

    fun makeEnrollKeyboardMarkup(id: String): InlineKeyboardMarkup {
        val inlineKeyboard = InlineKeyboardMarkup(
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
        return inlineKeyboard
    }

    fun makeApproveKeyboardMarkup(id: String): InlineKeyboardMarkup {
        val inlineKeyboard = InlineKeyboardMarkup(
            arrayOf(
                InlineKeyboardButton("✅通过").callbackData("approve:pass&$id"),
                InlineKeyboardButton("❎不通过").callbackData("approve:fail&$id"),
            )
        )
        return inlineKeyboard
    }
}