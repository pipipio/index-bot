package com.tgse.index.infrastructure.repository

import com.tgse.index.domain.repository.AwaitStatusRepository
import com.tgse.index.domain.service.AwaitStatusService
import org.springframework.stereotype.Repository

@Repository
class AwaitStatusRepositoryImpl : AwaitStatusRepository {

    private val status = HashMap<Long, AwaitStatusService.Await>()

    override fun setAwaitStatus(chatId: Long, await: AwaitStatusService.Await) {
        status[chatId] = await
    }

    override fun getAwaitStatus(chatId: Long): AwaitStatusService.Await? {
        return status[chatId]
    }

    override fun clearAwaitStatus(chatId: Long) {
        status[chatId] ?: return
        status.remove(chatId)
    }

}