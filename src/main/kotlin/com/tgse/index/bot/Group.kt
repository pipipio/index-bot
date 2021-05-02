package com.tgse.index.bot

import com.pengrad.telegrambot.model.request.ChatAction
import com.pengrad.telegrambot.model.request.ParseMode
import com.pengrad.telegrambot.request.AnswerCallbackQuery
import com.pengrad.telegrambot.request.GetChatAdministrators
import com.pengrad.telegrambot.request.SendChatAction
import com.pengrad.telegrambot.request.SendMessage
import com.pengrad.telegrambot.response.SendResponse
import com.tgse.index.bot.execute.BlacklistExecute
import com.tgse.index.datasource.Blacklist
import com.tgse.index.datasource.Elasticsearch
import com.tgse.index.datasource.Telegram
import com.tgse.index.factory.MsgFactory
import com.tgse.index.nick
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import com.tgse.index.provider.WatershedProvider.BotGroupRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.lang.RuntimeException
import java.util.*

@Service
class Group(
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider,
    private val telegram: Telegram,
    private val blacklist: Blacklist,
    private val blacklistExecute: BlacklistExecute,
    private val msgFactory: MsgFactory,
    private val elasticsearch: Elasticsearch,
) {

    private val logger = LoggerFactory.getLogger(Group::class.java)

    init {
        subscribeUpdate()
    }

    private fun subscribeUpdate() {
        watershedProvider.requestObservable.subscribe(
            { request ->
                try {
                    if (request !is BotGroupRequest) return@subscribe
                    // 输入状态
                    botProvider.sendTyping(request.chatId)
                    // 回执
                    when {
                        request.update.callbackQuery() != null ->
                            executeByButton(request)
                        request.update.message().text().startsWith("/") && request.update.message().text().endsWith("@${botProvider.username}") ->
                            executeByCommand(request)
                        else ->
                            executeByText(request)
                    }
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Group.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Group.complete")
            }
        )
    }

    private fun executeByText(request: BotGroupRequest) {
        // todo: 群组中查找群组……
    }

    private fun executeByCommand(request: BotGroupRequest) {
        // 获取命令内容
        val cmd = request.update.message().text().replaceFirst("/", "").replace("@${botProvider.username}", "")
        // 回执
        val sendMessage = when (cmd) {
            "start" -> msgFactory.makeReplyMsg(request.chatId, "only-private")
            "enroll", "update" -> executeByEnrollOrUpdate(request)
            // todo: 开启后，可以在群组中查找群组……
            "list" -> msgFactory.makeReplyMsg(request.chatId, "only-private")
            "mine" -> msgFactory.makeReplyMsg(request.chatId, "only-private")
            // todo： 配置是否开启索引服务群组 /list
            "setting" -> msgFactory.makeReplyMsg(request.chatId, "setting")
            "help" -> msgFactory.makeReplyMsg(request.chatId, "help-group")
            else -> msgFactory.makeReplyMsg(request.chatId, "can-not-understand")
        } ?: return
        sendMessage.disableWebPagePreview(true)
        sendMessage.parseMode(ParseMode.HTML)
        botProvider.sendAutoDeleteMessage(sendMessage)
    }

    private fun executeByButton(request: BotGroupRequest) {
        val answer = AnswerCallbackQuery(request.update.callbackQuery().id())
        botProvider.send(answer)

        val callbackData = request.update.callbackQuery().data()
        when {
            callbackData.startsWith("page") -> {

            }
        }
    }

    private fun executeByEnrollOrUpdate(request: BotGroupRequest): SendMessage? {
        // 校验bot权限
        if (!checkMyAuthority(request.chatId))
            return msgFactory.makeReplyMsg(request.chatId, "group-bot-authority")
        // 校验提交者权限
        val user = request.update.message().from()
        if (!checkUserAuthority(request.chatId, user.id().toLong()))
            return msgFactory.makeReplyMsg(request.chatId, "group-user-authority")
        // 人员黑名单检测
        val userBlack = blacklist.get(user.id().toLong())
        if (userBlack != null) {
            val telegramPerson = Telegram.TelegramPerson(user.id().toLong(), user.username(), user.nick(), null)
            blacklistExecute.notify(request.chatId, telegramPerson)
            return null
        }
        // 群组信息
        val telegramGroup = telegram.getTelegramGroupFromChat(request.chatId) ?: throw RuntimeException("群组信息获取失败")
        // 收录对象黑名单检测
        val recordBlack = blacklist.get(telegramGroup.chatId!!)
        if (recordBlack != null) {
            blacklistExecute.notify(request.chatId, telegramGroup)
            return null
        }
        // 入库
        val enroll = Elasticsearch.Enroll(
            UUID.randomUUID().toString(),
            Telegram.TelegramModType.Group,
            telegramGroup.chatId,
            telegramGroup.title,
            telegramGroup.description,
            null,
            null,
            telegramGroup.username,
            telegramGroup.link,
            telegramGroup.members,
            Date().time,
            user.id().toLong(),
            user.nick()
        )
        val createEnroll = elasticsearch.addEnroll(enroll)
        if (!createEnroll) throw RuntimeException("群组信息存储失败")
        // 回执
        val sendMessage = msgFactory.makeEnrollMsg(user.id().toLong(), enroll.uuid)
        botProvider.send(sendMessage)
        return msgFactory.makeReplyMsg(request.chatId, "pls-check-private")
    }

    /**
     * 检查bot权限
     * 必须为管理员且有邀请用户的权限
     */
    private fun checkMyAuthority(chatId: Long): Boolean {
        val getAdministrators = GetChatAdministrators(chatId)
        val administrators = botProvider.send(getAdministrators)
        val me = administrators.administrators().firstOrNull { it.user().username() == botProvider.username }
        return me != null && me.canInviteUsers()
    }

    /**
     * 检查用户权限
     * 必须为管理员
     */
    private fun checkUserAuthority(chatId: Long, userId: Long): Boolean {
        val getAdministrators = GetChatAdministrators(chatId)
        val administrators = botProvider.send(getAdministrators)
        val user = administrators.administrators().firstOrNull { it.user().id().toLong() == userId }
        return user != null
    }

}