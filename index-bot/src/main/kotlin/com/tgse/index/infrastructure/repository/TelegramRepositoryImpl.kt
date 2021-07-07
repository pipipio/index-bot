package com.tgse.index.infrastructure.repository

import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetChatMembersCount
import com.tgse.index.ProxyProperties
import com.tgse.index.domain.repository.TelegramRepository
import com.tgse.index.domain.service.TelegramService
import com.tgse.index.infrastructure.provider.BotProvider
import org.jsoup.Jsoup
import org.springframework.stereotype.Repository
import java.net.InetSocketAddress
import java.net.Proxy

@Repository
class TelegramRepositoryImpl(
    private val botProvider: BotProvider,
    private val proxyProperties: ProxyProperties
) : TelegramRepository {

    private val proxy: Proxy? = run {
        if (proxyProperties.enabled) {
            val socketAddress = InetSocketAddress(proxyProperties.ip, proxyProperties.port)
            Proxy(proxyProperties.type, socketAddress)
        } else {
            null
        }
    }

    /**
     * 公开群组、频道、机器人
     */
    override fun getTelegramModFromWeb(username: String): TelegramService.TelegramMod? {
        if (username.isEmpty()) return null
        val connect = Jsoup.connect("https://t.me/$username")
        val doc =
            if (proxyProperties.enabled) connect.proxy(proxy).get()
            else connect.get()

        val isNotFound = doc.select(".tgme_page_wrap .tgme_page .tgme_page_icon").html().contains("tgme_icon_user")
        val title = doc.select(".tgme_page_wrap .tgme_page .tgme_page_title span").text()
        val about = doc.select(".tgme_page_wrap .tgme_page .tgme_page_description").html()
        val members = doc.select(".tgme_page_wrap .tgme_page .tgme_page_extra").text()

        var fixedDescription: String? = about.replace("<[^>]+>".toRegex(), "")
        fixedDescription = if (fixedDescription!!.isEmpty() || fixedDescription.isBlank()) null else fixedDescription
        val fixedMembers = when {
            members.contains(",") -> members.split(',')[0].replace("members", "").replace(" ","").toLong()
            members.contains("subscriber") -> members.replace("subscribers", "").replace("subscriber", "").replace(" ","").toLong()
            else -> 0L
        }

        return when {
            isNotFound -> null
            members.contains("online") -> TelegramService.TelegramGroup(null, username, null, title, fixedDescription, fixedMembers)
            members.contains("subscriber") -> TelegramService.TelegramChannel(username, title, fixedDescription, fixedMembers)
            members.toLowerCase().endsWith("bot") -> TelegramService.TelegramBot(username, title, fixedDescription)
            else -> TelegramService.TelegramPerson(null, username, title, fixedDescription)
        }
    }

    /**
     * 群组
     */
    override fun getTelegramGroupFromChat(id: Long): TelegramService.TelegramGroup? {
        return try {
            val getChat = GetChat(id)
            val chat = botProvider.send(getChat).chat()

            val getChatMembersCount = GetChatMembersCount(id)
            val count = botProvider.send(getChatMembersCount).count()

            val link = if (chat.username() != null) null else chat.inviteLink()
            TelegramService.TelegramGroup(id, chat.username(), link, chat.title(), chat.description(), count.toLong())
        } catch (e: Throwable) {
            botProvider.sendErrorMessage(e)
            null
        }
    }

}