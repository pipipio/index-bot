package com.tgse.index.datasource

import com.pengrad.telegrambot.request.GetChat
import com.pengrad.telegrambot.request.GetChatMembersCount
import com.tgse.index.provider.BotProvider
import com.tgse.index.setting.ProxyProperties
import org.springframework.stereotype.Component
import org.jsoup.Jsoup
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 获取群组、频道、bot信息
 */
@Component
class Telegram(
    private val botProvider: BotProvider,
    private val proxyProperties: ProxyProperties
) {

    enum class TelegramModType {
        Channel,
        Group,
        Bot,
        Person
    }

    interface TelegramMod {
        val username: String?
        val title: String
        val description: String?
    }

    data class TelegramChannel(
        override val username: String,
        override val title: String,
        override val description: String?,
        val members: Long
    ) : TelegramMod

    data class TelegramGroup(
        val chatId: Long?,
        override val username: String?,
        val link: String?,
        override val title: String,
        override val description: String?,
        val members: Long
    ) : TelegramMod

    data class TelegramBot(
        override val username: String,
        override val title: String,
        override val description: String?
    ) : TelegramMod

    data class TelegramPerson(
        val chatId: Long?,
        override val username: String,
        override val title: String,
        override val description: String?
    ) : TelegramMod

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
    fun getTelegramModFromWeb(username: String): TelegramMod? {
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
            members.contains(",") -> members.split(',')[0].replace("members", "").trim().toLong()
            members.contains("subscriber") -> members.replace("subscribers", "").replace("subscriber", "").trim().toLong()
            else -> 0L
        }

        return when {
            isNotFound -> null
            members.contains("online") -> TelegramGroup(null, username, null, title, fixedDescription, fixedMembers)
            members.contains("subscriber") -> TelegramChannel(username, title, fixedDescription, fixedMembers)
            members.toLowerCase().endsWith("bot") -> TelegramBot(username, title, fixedDescription)
            else -> TelegramPerson(null, username, title, fixedDescription)
        }
    }

    /**
     * 群组
     */
    fun getTelegramGroupFromChat(id: Long): TelegramGroup? {
        return try {
            val getChat = GetChat(id)
            val chat = botProvider.send(getChat).chat()

            val getChatMembersCount = GetChatMembersCount(id)
            val count = botProvider.send(getChatMembersCount).count()

            val link = if (chat.username() != null) null else chat.inviteLink()
            TelegramGroup(id, chat.username(), link, chat.title(), chat.description(), count.toLong())
        } catch (e: Throwable) {
            botProvider.sendErrorMessage(e)
            null
        }
    }

}