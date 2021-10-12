package com.tgse.index.infrastructure.repository

import com.pengrad.telegrambot.model.Chat
import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetChatMembersCount
import com.tgse.index.domain.repository.TelegramRepository
import com.tgse.index.domain.service.TelegramService
import com.tgse.index.infrastructure.provider.BotProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class TelegramRepositoryImpl(
    private val botProvider: BotProvider
) : TelegramRepository {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 公开群组、频道
     */
    override fun getTelegramMod(username: String): TelegramService.TelegramMod? {
        return try {
            if (username.isEmpty()) return null
            val getChat = GetChat("@$username")
            val getChatResponse = botProvider.send(getChat)
            val chat = getChatResponse.chat() ?: return null
            val getChatMembersCount = GetChatMembersCount("@$username")
            val getChatMembersCountResponse = botProvider.send(getChatMembersCount)
            val membersCount = getChatMembersCountResponse.count() ?: 0

            return when (chat.type()) {
                Chat.Type.group, Chat.Type.supergroup ->
                    TelegramService.TelegramGroup(
                        chat.id(),
                        username,
                        chat.inviteLink(),
                        chat.title(),
                        chat.description(),
                        membersCount.toLong()
                    )
                Chat.Type.channel ->
                    TelegramService.TelegramChannel(
                        username,
                        chat.title(),
                        chat.description(),
                        membersCount.toLong()
                    )
                else -> null
            }
        } catch (t: Throwable) {
            logger.error("get telegram info error,the telegram username is '$username'", t)
            null
        }
    }

    /**
     * 群组
     */
    override fun getTelegramMod(id: Long): TelegramService.TelegramGroup? {
        return try {
            val getChat = GetChat(id)
            val chat = botProvider.send(getChat).chat()

            val getChatMembersCount = GetChatMembersCount(id)
            val count = botProvider.send(getChatMembersCount).count()

            val link = if (chat.username() != null) null else chat.inviteLink()
            TelegramService.TelegramGroup(id, chat.username(), link, chat.title(), chat.description(), count.toLong())
        } catch (t: Throwable) {
            logger.error("get telegram info error,the telegram chatId is '$id'", t)
            null
        }
    }

}