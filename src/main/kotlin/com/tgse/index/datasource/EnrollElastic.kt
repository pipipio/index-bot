package com.tgse.index.datasource

import com.pengrad.telegrambot.model.User
import com.tgse.index.provider.ElasticsearchProvider
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
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
        val createUserNick: String,
        val isSubmit: Boolean,
        val approve: Boolean?
    )

    private val submitEnrollSubject = BehaviorSubject.create<Enroll>()
    val submitEnrollObservable: Observable<Enroll> = submitEnrollSubject.distinct()

    private val submitApproveSubject = BehaviorSubject.create<Triple<Enroll, User, Boolean>>()
    val submitApproveObservable: Observable<Triple<Enroll, User, Boolean>> = submitApproveSubject.distinct()

    fun searchEnrolls(user: User, from: Int, size: Int): Pair<MutableList<Enroll>, Long> {
        try{
            val searchRequest = SearchRequest(index)
            val searchSourceBuilder = SearchSourceBuilder()
            val creatorMatch = QueryBuilders.matchQuery("createUser", user.id())
            val statusMatch = QueryBuilders.matchQuery("isSubmit", true)
            val approveMatch = QueryBuilders.matchQuery("approve", null)
            val boolQuery = QueryBuilders.boolQuery().must(creatorMatch).must(statusMatch).must(approveMatch)
            searchSourceBuilder.query(boolQuery).from(from).size(size).sort("createTime", SortOrder.DESC)
            searchRequest.source(searchSourceBuilder)
            val response = elasticsearchProvider.search(searchRequest)

            val enrolls = arrayListOf<Enroll>()
            response.hits.hits.forEach {
                val enroll = generateEnrollFromHashMap(it.id, it.sourceAsMap)
                enrolls.add(enroll)
            }
            val totalCount = response.hits.totalHits?.value ?: 0L
            return Pair(enrolls.toMutableList(), totalCount)
        } catch (e: Throwable) {
            return Pair(mutableListOf(), 0L)
        }
    }

    fun addEnroll(enroll: Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val indexRequest = IndexRequest(index)
        indexRequest.id(enroll.uuid).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    fun updateEnroll(enroll: Enroll): Boolean {
        val sourceEnroll = getEnroll(enroll.uuid)!!
        if (sourceEnroll.isSubmit != enroll.isSubmit) throw RuntimeException("此函数不允许修改状态")
        return updateEnrollDetail(enroll)
    }

    fun submitEnroll(uuid: String) {
        val enroll = getEnroll(uuid)!!
        if (enroll.isSubmit) return
        val newEnroll = enroll.copy(isSubmit = true)
        updateEnrollDetail(newEnroll)
        submitEnrollSubject.onNext(newEnroll)
    }

    private fun updateEnrollDetail(enroll: Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val updateRequest = UpdateRequest(index, enroll.uuid).doc(builder)
        return elasticsearchProvider.updateDocument(updateRequest)
    }

    fun approveEnroll(uuid: String, manager: User, isPassed: Boolean) {
        val enroll = getEnroll(uuid)!!
        val newEnroll = enroll.copy(approve = isPassed)
        updateEnroll(newEnroll)
        val triple = Triple(newEnroll, manager, isPassed)
        submitApproveSubject.onNext(triple)
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

    fun getSubmittedEnrollByUsername(username: String): Enroll? {
        val usernameMatch = QueryBuilders.matchQuery("username",username)
        val statusMatch = QueryBuilders.matchQuery("isSubmit",true)
        val queryBuilder = QueryBuilders.boolQuery().must(usernameMatch).must(statusMatch)
        return getSubmittedEnrollByQuery(queryBuilder)
    }

    fun getSubmittedEnrollByChatId(chatId: Long): Enroll? {
        val chatIdMatch = QueryBuilders.matchQuery("chatId", chatId)
        val statusMatch = QueryBuilders.matchQuery("isSubmit",true)
        val queryBuilder = QueryBuilders.boolQuery().must(chatIdMatch).must(statusMatch)
        return getSubmittedEnrollByQuery(queryBuilder)

    }

    private fun getSubmittedEnrollByQuery(queryBuilder: BoolQueryBuilder): Enroll? {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.query(queryBuilder)
        searchRequest.source(searchSourceBuilder)
        val response = elasticsearchProvider.search(searchRequest)
        return if (response.hits.totalHits!!.value < 1) null
        else generateEnrollFromHashMap(response.hits.hits[0].id, response.hits.hits[0].sourceAsMap)
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
        builder.field("isSubmit", enroll.isSubmit)
        builder.field("approve", enroll.approve)
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
            map["isSubmit"] as Boolean,
            map["approve"] as Boolean?
        )
    }

}