package com.tgse.index.bot.execute

import com.tgse.index.MismatchException
import com.tgse.index.datasource.AwaitStatus
import com.tgse.index.datasource.RecordElastic
import com.tgse.index.msgFactory.NormalMsgFactory
import com.tgse.index.msgFactory.RecordMsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import org.springframework.stereotype.Component

@Component
class RecordExecute(
    private val botProvider: BotProvider,
    private val normalMsgFactory: NormalMsgFactory,
    private val recordMsgFactory: RecordMsgFactory,
    private val recordElastic: RecordElastic,
    private val awaitStatus: AwaitStatus
) {

    fun executeByRecordButton(request: WatershedProvider.BotRequest) {
        val callbackData = request.update.callbackQuery().data()
        val callbackDataVal = callbackData.replace("update:", "").replace("record-classification:", "").split("&")
        val field = callbackDataVal[0]
        val record = recordElastic.getRecord(callbackDataVal[1])!!
        when {
            // 通过按钮修改收录申请信息
            callbackData.startsWith("record-classification:") -> {
                // 修改数据
                val newRecord = record.copy(classification = field)
                recordElastic.updateRecord(newRecord)
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                // 回执新消息
                val msg = recordMsgFactory.makeRecordMsg(request.chatId!!, newRecord)
                botProvider.send(msg)
            }
            // 通过文字修改收录申请信息
            arrayOf("title", "about", "tags").contains(field) -> {
                awaitStatus.setAwaitStatus(request.chatId!!, AwaitStatus.Await(request.messageId!!, callbackData))
                val msg = normalMsgFactory.makeReplyMsg(request.chatId!!, "enroll-update-$field")
                botProvider.send(msg)
            }
            // 通过按钮修改收录申请信息
            field == "classification" -> {
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                // 回执新消息
                val msg = recordMsgFactory.makeRecordChangeClassificationMsg(request.chatId!!, record)
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