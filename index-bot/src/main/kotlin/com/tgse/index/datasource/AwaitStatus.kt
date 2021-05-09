package com.tgse.index.datasource

import com.tgse.index.provider.BotProvider
import org.springframework.stereotype.Component

@Component
class AwaitStatus(
    private val botProvider: BotProvider
) {

    data class Await(val messageId: Int, val callbackData: String)

    private val status = HashMap<Long, Await>()

    /**
     * 设置状态
     */
    fun setAwaitStatus(chatId: Long, await: Await) {
        status[chatId] = await
    }

    fun getAwaitStatus(chatId: Long): Await? {
        return status[chatId]
    }

    /**
     * 状态处理完毕
     */
    fun applyAwaitStatus(chatId: Long) {
        val chatAwaitStatus = status[chatId]
        if (chatAwaitStatus != null) {
            botProvider.sendDeleteMessage(chatId, chatAwaitStatus.messageId)
            status.remove(chatId)
        }
    }

    /**
     * 取消状态
     */
    fun clearAwaitStatus(chatId: Long) {
        val chatAwaitStatus = status[chatId]
        if (chatAwaitStatus != null) {
            status.remove(chatId)
        }
    }

}