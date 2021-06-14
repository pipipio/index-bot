package com.tgse.index.area.execute

import com.tgse.index.datasource.Blacklist
import com.tgse.index.datasource.EnrollElastic
import com.tgse.index.datasource.Telegram
import com.tgse.index.datasource.nick
import com.tgse.index.area.msgFactory.NormalMsgFactory
import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import org.springframework.stereotype.Component
import java.util.*

@Component
class BlacklistExecute(
    private val botProvider: BotProvider,
    private val msgFactory: NormalMsgFactory,
    private val enrollElastic: EnrollElastic,
    private val blacklist: Blacklist,
) {

    fun executeByBlacklistButton(request: WatershedProvider.BotRequest) {
        val manager = request.update.callbackQuery().from().nick()

        val callbackData = request.update.callbackQuery().data()
        val callbackDataVal = callbackData.replace("blacklist:", "").split("&")
        val oper = callbackDataVal[0]
        val type = Blacklist.BlackType.valueOf(callbackDataVal[1])
        val enrollUUID = callbackDataVal[2]
        val enroll = enrollElastic.getEnroll(enrollUUID)!!

        // 检查是否已在黑名单
        val blackExist = when (type) {
            Blacklist.BlackType.Record -> {
                if (enroll.chatId != null) blacklist.get(enroll.chatId)
                else blacklist.get(enroll.username!!)
            }
            Blacklist.BlackType.User -> {
                blacklist.get(enroll.createUser)
            }
        }
        when (oper) {
            // 加入黑名单
            "join" -> {
                // 若不存在则添加至黑名单，若存在则升级黑名单
                val black = if (blackExist == null) {
                    val newBlack = Blacklist.Black(
                        UUID.randomUUID().toString(),
                        type,
                        when (type) {
                            Blacklist.BlackType.Record -> enroll.title
                            Blacklist.BlackType.User -> enroll.createUserNick
                        },
                        1,
                        when (type) {
                            Blacklist.BlackType.Record -> enroll.chatId
                            Blacklist.BlackType.User -> enroll.createUser
                        },
                        when (type) {
                            Blacklist.BlackType.Record -> enroll.username
                            Blacklist.BlackType.User -> null
                        },
                        Date().time
                    )
                    blacklist.add(newBlack)
                    newBlack
                } else {
                    val newBlack = blackExist.copy(
                        displayName = when (type) {
                            Blacklist.BlackType.Record -> enroll.title
                            Blacklist.BlackType.User -> enroll.createUserNick
                        },
                        level = blackExist.level + 1,
                        unfreezeTime = calcUnfreezeTime(blackExist.level + 1)
                    )
                    blacklist.update(newBlack)
                    newBlack
                }
                val msg = msgFactory.makeBlacklistJoinedReplyMsg(request.chatId!!, "blacklist-join", manager, black)
                botProvider.send(msg)
            }
            // 移出黑名单
            "left" -> {
                if (blackExist == null) return
                blacklist.delete(blackExist.uuid)
                val msg = msgFactory.makeBlacklistJoinedReplyMsg(request.chatId!!, "blacklist-left", manager, blackExist)
                botProvider.send(msg)
            }
        }
    }

    fun notify(chatId: Long, telegramMod: Telegram.TelegramMod) {
        val type = when (telegramMod) {
            is Telegram.TelegramGroup -> "群组"
            is Telegram.TelegramChannel -> "频道"
            is Telegram.TelegramBot -> "机器人"
            is Telegram.TelegramPerson -> "用户"
            else -> throw RuntimeException("不应执行到此处")
        }
        val msg =
            if (telegramMod is Telegram.TelegramPerson)
                msgFactory.makeBlacklistExistReplyMsg(chatId, "blacklist-exist-user", type)
            else
                msgFactory.makeBlacklistExistReplyMsg(chatId, "blacklist-exist-record", type)
        botProvider.send(msg)
    }

    private fun calcUnfreezeTime(level: Int): Long {
        val now = Date().time
        return when (level) {
            // 一周
            1 -> now + 604800000
            // 一个月
            2 -> now + 2592000000
            // 六个月
            3 -> now + 15552000000
            // 一年
            4 -> now + 31536000000
            // 五年
            else -> now + 157680000000
        }
    }

}