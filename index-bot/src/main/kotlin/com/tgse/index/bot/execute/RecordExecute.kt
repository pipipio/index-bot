package com.tgse.index.bot.execute

import com.tgse.index.MismatchException
import com.tgse.index.datasource.*
import com.tgse.index.msgFactory.NormalMsgFactory
import com.tgse.index.msgFactory.RecordMsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import org.springframework.stereotype.Component
import java.util.*

@Component
class RecordExecute(
    private val blacklistExecute: BlacklistExecute,
    private val botProvider: BotProvider,
    private val normalMsgFactory: NormalMsgFactory,
    private val recordMsgFactory: RecordMsgFactory,
    private val enrollElastic: EnrollElastic,
    private val recordElastic: RecordElastic,
    private val telegram: Telegram,
    private val blacklist: Blacklist,
    private val awaitStatus: AwaitStatus
) {

    fun executeByRecordButton(request: WatershedProvider.BotRequest) {
        val user = request.update.callbackQuery().from()
        val callbackData = request.update.callbackQuery().data()
        val callbackDataVal = callbackData.replace("update:", "").replace("record-class:", "").split("&")
        val field = callbackDataVal[0]
        val record = recordElastic.getRecord(callbackDataVal[1])!!
        when {
            // 通过按钮修改收录申请信息
            callbackData.startsWith("record-class:") -> {
                // 修改数据
                val newRecord = record.copy(classification = field)
                recordElastic.updateRecord(newRecord)
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                // 回执新消息
                val msg = recordMsgFactory.makeRecordMsg(request.chatId!!, newRecord)
                botProvider.send(msg)
            }
            // 通过文字修改收录申链接
            field == "link" -> {
                if (record.type == Telegram.TelegramModType.Group) {
                    val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "update-link-group")
                    botProvider.send(msg)
                    return
                }
                awaitStatus.setAwaitStatus(request.chatId!!, AwaitStatus.Await(request.messageId!!, callbackData))
                val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "update-$field")
                botProvider.send(msg)
            }
            // 通过文字修改收录申请信息
            arrayOf("title", "about", "tags").contains(field) -> {
                awaitStatus.setAwaitStatus(request.chatId!!, AwaitStatus.Await(request.messageId!!, callbackData))
                val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "update-$field")
                botProvider.send(msg)
            }
            // 通过按钮修改收录申请信息
            field == "record-class" -> {
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                // 回执新消息
                val msg = recordMsgFactory.makeRecordChangeClassificationMsg(request.chatId!!, record)
                botProvider.send(msg)
            }
            // 移除收录
            field == "remove" -> {
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                recordElastic.deleteRecord(record.uuid, user)
                // 回执新消息
                val msg = normalMsgFactory.makeRemoveRecordReplyMsg(request.chatId!!, record.title)
                botProvider.send(msg)
            }
        }
    }

    fun executeByStatus(request: WatershedProvider.BotRequest) {
        val statusCallbackData = awaitStatus.getAwaitStatus(request.chatId!!)!!.callbackData
        val callbackDataVal = statusCallbackData.replace("update:", "").split("&")
        val field = callbackDataVal[0]
        val uuid = callbackDataVal[1]
        val msgContent = request.update.message().text()
        try {
            val record = recordElastic.getRecord(uuid)!!
            val newRecord = when (field) {
                "link" -> {
                    // 获取收录内容
                    val username = request.update.message().text().replaceFirst("@", "").replaceFirst("https://t.me/", "")
                    if (record.username == username) throw MismatchException("链接未发生改变")
                    val telegramMod = telegram.getTelegramModFromWeb(username)
                    // 收录对象黑名单检测
                    val recordBlack = blacklist.get(username)
                    if (recordBlack != null && telegramMod != null) {
                        blacklistExecute.notify(request.chatId!!, telegramMod)
                        return
                    }
                    // 检测是否已有提交或已收录
                    val enrollExist = enrollElastic.getSubmittedEnrollByUsername(username)
                    val recordExist = recordElastic.getRecordByUsername(username)
                    if (enrollExist != null || recordExist != null) {
                        val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "exist")
                        botProvider.send(msg)
                        return
                    }
                    val type = when (telegramMod) {
                        is Telegram.TelegramGroup -> Telegram.TelegramModType.Group
                        is Telegram.TelegramChannel -> Telegram.TelegramModType.Channel
                        is Telegram.TelegramBot -> Telegram.TelegramModType.Bot
                        else -> throw MismatchException("链接不存在")
                    }
                    if(record.type != type) throw  throw MismatchException("类型不匹配")
                    record.copy(link = msgContent)
                }
                "title" -> {
                    if (msgContent.length > 26) throw MismatchException("标题太长，修改失败")
                    record.copy(title = msgContent)
                }
                "about" -> {
                    record.copy(description = msgContent)
                }
                "tags" -> {
                    val tags = mutableListOf<String>()
                    """(?<=#)[^\s#]+""".toRegex().findAll(msgContent).forEach {
                        val tag = "#${it.value}"
                        if (!tags.contains(tag))
                            tags.add(tag)
                    }
                    record.copy(tags = tags)
                }
                else -> throw RuntimeException("record request")
            }
            recordElastic.updateRecord(newRecord)
            // 清除状态
            awaitStatus.applyAwaitStatus(request.chatId!!)
            // 回执新消息
            val msg = recordMsgFactory.makeRecordMsg(request.chatId!!, newRecord)
            botProvider.send(msg)
        } catch (e: Throwable) {
            when (e) {
                is MismatchException -> {
                    val msg = normalMsgFactory.makeExceptionMsg(request.chatId!!, e)
                    botProvider.send(msg)
                }
                else -> throw  e
            }
        }
    }

}