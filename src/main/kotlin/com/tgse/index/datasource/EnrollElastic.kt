package com.tgse.index.datasource

import com.tgse.index.provider.ElasticsearchProvider
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.springframework.stereotype.Component

/**
 * 数据引擎
 */
@Component
class EnrollElastic(
    private val elasticsearchProvider: ElasticsearchProvider
) {

    private val index = "enroll"

    init {
        initializeEnroll()
    }

    private fun initializeEnroll() {
        val exist = elasticsearchProvider.checkIndexExist(index)
        if (exist) return
        // if (exist) elasticsearchProvider.deleteIndex(index)
        elasticsearchProvider.createIndex(index)
    }

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
        val createUserNick: String
    )

    private val submitEnrollSubject = BehaviorSubject.create<String>()
    val submitEnrollObservable: Observable<String> = submitEnrollSubject.distinct()

    private val submitApproveSubject = BehaviorSubject.create<Pair<String, Boolean>>()
    val submitApproveObservable: Observable<Pair<String, Boolean>> = submitApproveSubject.distinct()

    fun addEnroll(enroll: Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val indexRequest = IndexRequest(index)
        indexRequest.id(enroll.uuid).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    fun updateEnroll(enroll: Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val updateRequest = UpdateRequest(index, enroll.uuid).doc(builder)
        return elasticsearchProvider.updateDocument(updateRequest)
    }

    fun submitEnroll(uuid: String) {
        val enroll = getEnroll(uuid)!!
        submitEnrollSubject.onNext(enroll.uuid)
    }

    fun approveEnroll(uuid: String, isPassed: Boolean) {
        val pair = Pair(uuid, isPassed)
        submitApproveSubject.onNext(pair)
    }

    fun deleteEnroll(uuid: String) {
        val deleteRequest = DeleteRequest(index, uuid)
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    fun getEnroll(uuid: String): Enroll? {
        val request = GetRequest(index, uuid)
        val response = elasticsearchProvider.getDocument(request)
        if (!response.isExists) return null
        return generateEnrollFromHashMap(uuid, response.sourceAsMap)
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
        builder.field("username", enroll.username)
        builder.field("link", enroll.link)
        builder.field("members", enroll.members)
        builder.field("createTime", enroll.createTime)
        builder.field("createUser", enroll.createUser)
        builder.field("createUserName", enroll.createUserNick)
        builder.endObject()
        return builder
    }

    private fun generateEnrollFromHashMap(uuid: String, map: MutableMap<String, Any?>): Enroll {
        val tagsString = map["tags"].toString()
        val tags = when {
            tagsString.contains(" ") -> tagsString.split(" ")
            tagsString == "null" -> null
            else -> listOf(tagsString)
        }
        return Enroll(
            uuid,
            Telegram.TelegramModType.valueOf(map["type"] as String),
            when (map["chatId"]) {
                is Int -> (map["chatId"] as Int).toLong()
                is Long -> map["chatId"] as Long
                else -> null
            },
            map["title"] as String,
            map["description"] as String?,
            tags,
            map["classification"] as String?,
            map["username"] as String?,
            map["link"] as String?,
            when (map["members"]) {
                is Int -> (map["members"] as Int).toLong()
                is Long -> map["members"] as Long
                else -> null
            },
            map["createTime"] as Long,
            map["createUser"].toString().toLong(),
            map["createUserName"] as String,
        )
    }

}