package com.tgse.index.domain.service

import com.pengrad.telegrambot.model.User
import com.tgse.index.domain.repository.RecordRepository
import com.tgse.index.domain.repository.TelegramRepository
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import org.springframework.stereotype.Service
import java.util.*

@Service
class RecordService(
    private val recordRepository: RecordRepository,
    private val telegramRepository: TelegramRepository
) {

    data class Record(
        val uuid: String,
        val bulletinMessageId: Int?,
        val type: TelegramService.TelegramModType,
        val chatId: Long?,
        val title: String,
        val description: String?,
        /**
         * 包含#
         * 如：#apple #iphone
         */
        val tags: Collection<String>?,
        val classification: String?,
        val username: String?,
        val link: String?,
        val members: Long?,
        val createTime: Long,
        val createUser: Long,
        val updateTime: Long,
    )

    private val updateRecordSubject = BehaviorSubject.create<Record>()
    val updateRecordObservable: Observable<Record> = updateRecordSubject.distinct()

    private val deleteRecordSubject = BehaviorSubject.create<Pair<Record, User>>()
    val deleteRecordObservable: Observable<Pair<Record, User>> = deleteRecordSubject.distinct()

    fun searchRecordsByClassification(classification: String, from: Int, size: Int): Pair<MutableList<Record>, Long> {
        return recordRepository.searchRecordsByClassification(classification, from, size)
    }

    fun searchRecordsByKeyword(keyword: String, from: Int, size: Int): Pair<MutableList<Record>, Long> {
        return recordRepository.searchRecordsByKeyword(keyword, from, size)
    }

    fun searchRecordsByCreator(user: User, from: Int, size: Int): Pair<MutableList<Record>, Long> {
        return recordRepository.searchRecordsByCreator(user, from, size)
    }

    fun getRecordByUsername(username: String): Record? {
        return recordRepository.getRecordByUsername(username)
    }

    fun getRecordByChatId(chatId: Long): Record? {
        return recordRepository.getRecordByChatId(chatId)
    }

    fun addRecord(record: Record): Boolean {
        return recordRepository.addRecord(record)
    }

    fun updateRecord(record: Record) {
        val newRecord = record.copy(updateTime = Date().time)
        recordRepository.updateRecord(newRecord)
        updateRecordSubject.onNext(newRecord)
    }

    fun deleteRecord(uuid: String, manager: User) {
        val record = getRecord(uuid)!!
        recordRepository.deleteRecord(uuid, manager)
        deleteRecordSubject.onNext(Pair(record, manager))
    }


    fun getRecord(uuid: String): Record? {
        val record = recordRepository.getRecord(uuid) ?: return null
        try {
            // 24小时后更新人数信息
            val isOver24Hours = record.updateTime < Date().time - 24 * 60 * 60 * 1000
            val isHasMembers = record.members != null
            return if (isOver24Hours && isHasMembers) {
                val telegramMod =
                    if (record.username != null) telegramRepository.getTelegramModFromWeb(record.username)
                    else telegramRepository.getTelegramGroupFromChat(record.chatId!!)

                val members = when (telegramMod) {
                    is TelegramService.TelegramChannel -> telegramMod.members
                    is TelegramService.TelegramGroup -> telegramMod.members
                    else -> 0L
                }

                val newRecord = record.copy(members = members)
                updateRecord(newRecord)
                newRecord
            } else {
                record
            }
        } catch (e: Throwable) {
            return record
        }
    }

}