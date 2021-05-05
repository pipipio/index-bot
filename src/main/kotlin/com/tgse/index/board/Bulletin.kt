package com.tgse.index.board

import com.tgse.index.datasource.RecordElastic
import com.tgse.index.msgFactory.BulletinMsgFactory
import com.tgse.index.provider.BotProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 公告板
 */
@Service
class Bulletin(
    private val botProvider: BotProvider,
    private val bulletinMsgFactory: BulletinMsgFactory,
    private val recordElastic: RecordElastic,
    @Value("\${channel.bulletin.id}")
    private val bulletinChatId: Long
) {
    private val logger = LoggerFactory.getLogger(Bulletin::class.java)

    init {
        subscribeUpdateRecord()
        subscribeDeleteRecord()
    }

    /**
     * 发布公告
     */
    fun publish(record: RecordElastic.Record) {
        val msg = bulletinMsgFactory.makeBulletinMsg(bulletinChatId, record)
        val response = botProvider.send(msg)
        // 补充公告消息ID
        val newRecord = record.copy(bulletinMessageId = response.message().messageId())
        recordElastic.addRecord(newRecord)
    }

    /**
     * 同步数据-更新公告
     */
    private fun subscribeUpdateRecord() {
        recordElastic.updateRecordObservable.subscribe(
            { record ->
                try {
                    val msg = bulletinMsgFactory.makeBulletinMsg(bulletinChatId, record.bulletinMessageId!!, record)
                    botProvider.send(msg)
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Bulletin.subscribeUpdateRecord.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Bulletin.subscribeUpdateRecord.complete")
            }
        )
    }

    /**
     * 同步数据-删除公告
     */
    private fun subscribeDeleteRecord() {
        recordElastic.deleteRecordObservable.subscribe(
            { (record, _) ->
                try {
                    botProvider.sendDeleteMessage(bulletinChatId, record.bulletinMessageId!!)
                } catch (e: Throwable) {
                    botProvider.sendErrorMessage(e)
                    e.printStackTrace()
                }
            },
            { throwable ->
                throwable.printStackTrace()
                logger.error("Bulletin.subscribeDeleteRecord.error")
                botProvider.sendErrorMessage(throwable)
            },
            {
                logger.error("Bulletin.subscribeDeleteRecord.complete")
            }
        )
    }

}