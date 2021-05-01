package com.tgse.index.datasource

import com.tgse.index.provider.ElasticsearchProvider
import com.tgse.index.provider.WatershedProvider
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.springframework.stereotype.Component
import java.lang.RuntimeException

/**
 * 数据引擎
 */
@Component
class Elasticsearch(
    private val elasticsearchProvider: ElasticsearchProvider
) {

    data class Enroll(
        val uuid: String,
        val type: Telegram.TelegramModType,
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
        val createUserNick: String,
        val status: Boolean
    )

    private val submitEnrollSubject = BehaviorSubject.create<String>()
    val submitEnrollObservable: Observable<String> = submitEnrollSubject.distinct()

    private val submitApproveSubject = BehaviorSubject.create<WatershedProvider.BotRequest>()
    val submitApproveObservable: Observable<WatershedProvider.BotRequest> = submitApproveSubject.distinct()

    fun addEnroll(enroll: Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val indexRequest = IndexRequest(elasticsearchProvider.enrollIndexName)
        indexRequest.id(enroll.uuid).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    fun updateEnroll(enroll: Enroll): Boolean {
        val sourceEnroll = getEnroll(enroll.uuid)!!
        if (sourceEnroll.status != enroll.status) throw RuntimeException("此函数不允许修改状态")
        return updateEnrollDetail(enroll)
    }

    fun submitEnroll(uuid: String): Boolean {
        val enroll = getEnroll(uuid)
        if (enroll!!.status) return true
        val newEnroll = enroll.copy(status = true)
        val updateStatus = updateEnrollDetail(newEnroll)
        if (updateStatus)
            submitEnrollSubject.onNext(newEnroll.uuid)
        return updateStatus
    }

    private fun updateEnrollDetail(enroll: Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val updateRequest = UpdateRequest(elasticsearchProvider.enrollIndexName, enroll.uuid).doc(builder)
        return elasticsearchProvider.updateDocument(updateRequest)
    }

    fun deleteEnroll(uuid: String) {
        val deleteRequest = DeleteRequest(elasticsearchProvider.enrollIndexName, uuid)
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    fun getEnroll(uuid: String): Enroll? {
        val request = GetRequest(elasticsearchProvider.enrollIndexName, uuid)
        val response = elasticsearchProvider.getDocument(request)
        if (!response.isExists) return null
        val content = response.sourceAsMap
        val tagsString = content["tags"].toString()
        val tags = when {
            tagsString.contains(" ") -> tagsString.split(" ")
            tagsString == "null" -> null
            else -> listOf(tagsString)
        }

        return Enroll(
            uuid,
            Telegram.TelegramModType.valueOf(content["type"] as String),
            when (content["chatId"]) {
                is Int -> (content["chatId"] as Int).toLong()
                is Long -> content["chatId"] as Long
                else -> null
            },
            content["title"] as String,
            content["description"] as String?,
            tags,
            content["classification"] as String?,
            content["code"] as String?,
            content["link"] as String?,
            when (content["members"]) {
                is Int -> (content["members"] as Int).toLong()
                is Long -> content["members"] as Long
                else -> null
            },
            content["createTime"] as Long,
            content["createUser"].toString().toLong(),
            content["createUserName"] as String,
            content["status"] as Boolean,
        )
    }

    private fun generateXContentFromEnroll(enroll: Enroll): XContentBuilder {
        val tagsString =
            if (enroll.tags == null) null
            else enroll.tags.joinToString(" ")
        val builder = XContentFactory.jsonBuilder()
        builder.startObject()
        builder.field("type", enroll.type.name)
        builder.field("chatId", enroll.chatId)
        builder.field("title", enroll.title)
        builder.field("description", enroll.description)
        builder.field("tags", tagsString)
        builder.field("classification", enroll.classification)
        builder.field("code", enroll.username)
        builder.field("link", enroll.link)
        builder.field("members", enroll.members)
        builder.field("createTime", enroll.createTime)
        builder.field("createUser", enroll.createUser)
        builder.field("createUserName", enroll.createUserNick)
        builder.field("status", enroll.status)
        builder.endObject()
        return builder
    }

}