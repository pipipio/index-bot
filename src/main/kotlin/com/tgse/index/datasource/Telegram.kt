package com.tgse.index.datasource

import com.tgse.index.provider.BotProvider
import org.springframework.stereotype.Component
import org.jsoup.Jsoup

/**
 * 获取群组、频道、bot信息
 */
@Component
class Telegram(
    private val botProvider: BotProvider
) {

    interface TelegramMod {
        val id:String?
        val title: String
        val about: String
    }

    data class TelegramChannel(override val id: String, override val title: String, override val about: String, val members: Long) :
        TelegramMod
    data class TelegramGroup(override val id: String?, val link:String?, override val title: String, override val about: String, val members: Long) :
        TelegramMod
    data class TelegramBot(override val id: String,override val title: String, override val about: String) : TelegramMod
    data class TelegramPerson(override val id: String,override val title: String, override val about: String) :
        TelegramMod

    /**
     * 公开群组、频道、机器人
     */
    fun getModFromWeb(id: String): TelegramMod? {
        if (id.isEmpty()) return null
        val doc = Jsoup.connect("https://t.me/$id").get()

        val isNotFound = doc.select(".tgme_page_wrap .tgme_page .tgme_page_icon").html().contains("tgme_icon_user")
        val title = doc.select(".tgme_page_wrap .tgme_page .tgme_page_title span").text()
        val about = doc.select(".tgme_page_wrap .tgme_page .tgme_page_description").html()
        val members = doc.select(".tgme_page_wrap .tgme_page .tgme_page_extra").text()

        val fixedAbout = about.replace("<[^>]+>".toRegex(), "")
        val fixedMembers = when (true) {
            members.contains(",") -> members.split(',')[0].replace("members", "").replace(" ".toRegex(), "").toLong()
            members.contains("members") -> members.replace("members", "").replace(" ".toRegex(), "").toLong()
            else -> 0L
        }

        return when (true) {
            isNotFound -> null
            members.contains("online") -> TelegramGroup(id,null, title, fixedAbout, fixedMembers)
            members.contains("subscribers") -> TelegramChannel(id, title, fixedAbout, fixedMembers)
            members.toLowerCase().endsWith("bot") -> TelegramBot(id, title, fixedAbout)
            else -> TelegramPerson(id, title, fixedAbout)
        }
    }

    /**
     * 仅支持私有群组
     */
    fun getModFromChat(id:String){

    }

}