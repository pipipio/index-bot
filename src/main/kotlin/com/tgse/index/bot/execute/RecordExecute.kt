package com.tgse.index.bot.execute

import com.tgse.index.MismatchException
import com.tgse.index.board.Bulletin
import com.tgse.index.datasource.AwaitStatus
import com.tgse.index.datasource.EnrollElastic
import com.tgse.index.datasource.RecordElastic
import com.tgse.index.factory.MsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

@Component
class RecordExecute(
    private val bulletin: Bulletin,
    private val botProvider: BotProvider,
    private val msgFactory: MsgFactory,
    private val enrollElastic: EnrollElastic,
    private val recordElastic: RecordElastic,
    private val awaitStatus: AwaitStatus
) {
    enum class Type {
        Enroll,
        Approve
    }

    fun executeByEnrollButton(type: Type, request: WatershedProvider.BotRequest) {
        val callbackData = request.update.callbackQuery().data()
        val callbackDataVal =
            callbackData.replace("enroll:", "").replace("approve:", "").replace("classification:", "").split("&")
        val field = callbackDataVal[0]
        val enroll = enrollElastic.getEnroll(callbackDataVal[1])!!
        when {
            // 通过按钮修改收录申请信息
            callbackData.startsWith("classification:") -> {
                // 修改数据
                val newEnroll = enroll.copy(classification = field)
                enrollElastic.updateEnroll(newEnroll)
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                // 回执新消息
                val msg = when (type) {
                    Type.Enroll -> msgFactory.makeEnrollMsg(request.chatId!!, newEnroll)
                    Type.Approve -> msgFactory.makeApproveMsg(request.chatId!!, newEnroll)
                }
                botProvider.send(msg)
            }
            // 通过文字修改收录申请信息
            arrayOf("title", "about", "tags").contains(field) -> {
                awaitStatus.setAwaitStatus(request.chatId!!, AwaitStatus.Await(request.messageId!!, callbackData))
                val msg = msgFactory.makeReplyMsg(request.chatId!!, "enroll-update-$field")
                botProvider.send(msg)
            }
            // 通过按钮修改收录申请信息
            field == "classification" -> {
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                // 回执新消息
                val msg = when (type) {
                    Type.Enroll -> msgFactory.makeEnrollChangeClassificationMsg(request.chatId!!, enroll)
                    Type.Approve -> msgFactory.makeApproveChangeClassificationMsg(request.chatId!!, enroll)
                }
                botProvider.send(msg)
            }
            // 提交
            field == "submit" -> {
                // 提交之前必须选择分类
                if (enroll.classification == null) {
                    val msg = msgFactory.makeReplyMsg(request.chatId!!, "enroll-submit-verify-classification")
                    botProvider.send(msg)
                    return
                }
                // 提交信息
                enrollElastic.submitEnroll(enroll.uuid)
                awaitStatus.clearAwaitStatus(request.chatId!!)
                val editMsg = msgFactory.makeClearMarkupMsg(request.chatId!!, request.messageId!!)
                botProvider.send(editMsg)
                val msg = msgFactory.makeReplyMsg(request.chatId!!, "enroll-submit")
                botProvider.send(msg)
            }
            // 取消
            field == "cancel" -> {
                enrollElastic.deleteEnroll(enroll.uuid)
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                awaitStatus.clearAwaitStatus(request.chatId!!)
                val msg = msgFactory.makeReplyMsg(request.chatId!!, "cancel")
                botProvider.send(msg)
            }
            // 通过
            field == "pass" -> {
                // record
                val record = RecordElastic.Record(
                    UUID.randomUUID().toString(),
                    null,
                    enroll.type,
                    enroll.chatId,
                    enroll.title,
                    enroll.description,
                    enroll.tags,
                    enroll.classification,
                    enroll.username,
                    enroll.link,
                    enroll.members,
                    enroll.createTime,
                    enroll.createUser,
                    Date().time
                )
                bulletin.publish(record)
                // 回执
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                awaitStatus.clearAwaitStatus(request.chatId!!)
                val manager = request.update.callbackQuery().from()
                enrollElastic.approveEnroll(enroll.uuid, manager, true)
            }
            // 不通过
            field == "fail" -> {
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                awaitStatus.clearAwaitStatus(request.chatId!!)
                val manager = request.update.callbackQuery().from()
                enrollElastic.approveEnroll(enroll.uuid, manager, false)
            }
        }
    }

    fun executeByStatus(type: Type, request: WatershedProvider.BotRequest) {
        val statusCallbackData = awaitStatus.getAwaitStatus(request.chatId!!)!!.callbackData
        val callbackDataVal = statusCallbackData.replace("enroll:", "").replace("approve:", "").split("&")
        val field = callbackDataVal[0]
        val uuid = callbackDataVal[1]
        val msgContent = request.update.message().text()
        try {
            val enroll = enrollElastic.getEnroll(uuid)!!
            val newEnroll = when (field) {
                "title" -> {
                    if (msgContent.length > 26) throw MismatchException("标题太长，修改失败")
                    enroll.copy(title = msgContent)
                }
                "about" -> {
                    enroll.copy(description = msgContent)
                }
                "tags" -> {
                    val tags = mutableListOf<String>()
                    """(?<=#)[^\s#]+""".toRegex().findAll(msgContent).forEach {
                        val tag = "#${it.value}"
                        if (!tags.contains(tag))
                            tags.add(tag)
                    }
                    enroll.copy(tags = tags)
                }
                else -> throw RuntimeException("error request")
            }
            enrollElastic.updateEnroll(newEnroll)
            // 清除状态
            awaitStatus.applyAwaitStatus(request.chatId!!)
            // 回执新消息
            val msg = when (type) {
                Type.Enroll -> msgFactory.makeEnrollMsg(request.chatId!!, newEnroll)
                Type.Approve -> msgFactory.makeApproveMsg(request.chatId!!, newEnroll)
            }
            botProvider.send(msg)
        } catch (e: Throwable) {
            when (e) {
                is MismatchException -> {
                    val msg = msgFactory.makeExceptionMsg(request.chatId!!, e)
                    botProvider.send(msg)
                }
                else -> throw  e
            }
        }
    }

}