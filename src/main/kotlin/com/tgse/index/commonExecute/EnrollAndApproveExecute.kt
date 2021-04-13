package com.tgse.index.commonExecute

import com.tgse.index.MismatchException
import com.tgse.index.datasource.AwaitStatus
import com.tgse.index.datasource.Elasticsearch
import com.tgse.index.factory.MsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import org.springframework.stereotype.Component

@Component
class EnrollAndApproveExecute(
    private val botProvider: BotProvider,
    private val msgFactory: MsgFactory,
    private val elasticsearch: Elasticsearch,
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
        val uuid = callbackDataVal[1]
        when (true) {
            // 通过按钮修改收录申请信息
            callbackData.startsWith("classification") -> {
                // 修改数据
                val enroll = elasticsearch.getEnroll(uuid)!!
                val newEnroll = enroll.copy(classification = field)
                elasticsearch.updateEnroll(newEnroll)
                // 删除上一条消息
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                // 回执新消息
                val msg = when (type) {
                    Type.Enroll -> msgFactory.makeEnrollMsg(request.chatId!!, uuid)
                    Type.Approve -> msgFactory.makeApproveMsg(request.chatId!!, uuid)
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
                    Type.Enroll -> msgFactory.makeEnrollChangeClassificationMsg(request.chatId!!, uuid)
                    Type.Approve -> msgFactory.makeApproveChangeClassificationMsg(request.chatId!!, uuid)
                }
                botProvider.send(msg)
            }
            // 提交
            field == "submit" -> {
                elasticsearch.submitEnroll(uuid)
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                awaitStatus.clearAwaitStatus(request.chatId!!)
                val msg = msgFactory.makeReplyMsg(request.chatId!!, "enroll-submit")
                botProvider.send(msg)
            }
            // 取消
            field == "cancel" -> {
                elasticsearch.deleteEnroll(uuid)
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                awaitStatus.clearAwaitStatus(request.chatId!!)
                val msg = msgFactory.makeReplyMsg(request.chatId!!, "cancel")
                botProvider.send(msg)
            }
            // 通过
            field == "pass" -> {
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                awaitStatus.clearAwaitStatus(request.chatId!!)
                // todo: 审核日志
            }
            // 不通过
            field == "fail" -> {
                elasticsearch.deleteEnroll(uuid)
                botProvider.sendDeleteMessage(request.chatId!!, request.messageId!!)
                awaitStatus.clearAwaitStatus(request.chatId!!)
                val msg = msgFactory.makeReplyMsg(request.chatId!!, "cancel")
                botProvider.send(msg)
                // todo: 加入黑名单选项
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
            val enroll = elasticsearch.getEnroll(uuid)!!
            when (field) {
                "title" -> {
                    if (msgContent.length > 26) throw MismatchException("标题太长，修改失败")
                    val newEnroll = enroll.copy(title = msgContent)
                    elasticsearch.updateEnroll(newEnroll)
                }
                "about" -> {
                    val newEnroll = enroll.copy(description = msgContent)
                    elasticsearch.updateEnroll(newEnroll)
                }
                "tags" -> {
                    val tags = mutableListOf<String>()
                    """(?<=#)[^\s#]+""".toRegex().findAll(msgContent).forEach {
                        val tag = "#${it.value}"
                        if (!tags.contains(tag))
                            tags.add(tag)
                    }
                    if (tags.size < 1) throw MismatchException("格式有误，修改失败")
                    val newEnroll = enroll.copy(tags = tags)
                    elasticsearch.updateEnroll(newEnroll)
                }
            }
            // 清除状态
            awaitStatus.applyAwaitStatus(request.chatId!!)
            // 回执新消息
            val msg = when (type) {
                Type.Enroll -> msgFactory.makeEnrollMsg(request.chatId!!, uuid)
                Type.Approve -> msgFactory.makeApproveMsg(request.chatId!!, uuid)
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