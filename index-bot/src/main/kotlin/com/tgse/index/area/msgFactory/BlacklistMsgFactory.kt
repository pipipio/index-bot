package com.tgse.index.area.msgFactory

import com.pengrad.telegrambot.request.SendMessage
import com.tgse.index.datasource.*
import com.tgse.index.provider.BotProvider
import org.springframework.stereotype.Component

@Component
class BlacklistMsgFactory(
    override val reply: Reply,
    override val botProvider: BotProvider
) : BaseMsgFactory(reply, botProvider) {

    fun makeBlacklistJoinedReplyMsg(
        chatId: Long,
        replyType: String,
        manager: String,
        black: Blacklist.Black
    ): SendMessage {
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

}