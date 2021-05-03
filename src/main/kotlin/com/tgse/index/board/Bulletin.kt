package com.tgse.index.board

import com.tgse.index.datasource.RecordElastic
import com.tgse.index.factory.MsgFactory
import com.tgse.index.provider.BotProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class Bulletin(
    private val botProvider: BotProvider,
    private val msgFactory: MsgFactory,
    private val recordElastic: RecordElastic,
    @Value("\${channel.bulletin.id}")
    private val bulletinChannelChatId: Long
) {
    private val logger = LoggerFactory.getLogger(Bulletin::class.java)

    init {
        subscribeDeleteRecord()
    }

    private fun subscribeDeleteRecord() {
        recordElastic.deleteRecordObservable.subscribe(
            { next ->
                try {
                    botProvider.sendDeleteMessage(bulletinChannelChatId, next.first.bulletinMessageId!!)
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Bulletin.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Bulletin.complete")
            }
        )
    }

    fun publish(record: RecordElastic.Record) {
        val msg = msgFactory.makeBulletinMsg(bulletinChannelChatId, record)
        val response = botProvider.send(msg)
        // 补充公告消息ID
        val newRecord = record.copy(bulletinMessageId = response.message().messageId())
        recordElastic.addRecord(newRecord)
    }

}