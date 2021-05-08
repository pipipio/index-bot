package com.tgse.index.datasource

import com.tgse.index.provider.ElasticsearchProvider
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.springframework.stereotype.Component

/**
 * 黑名单
 *
 * 无奈之举
 * 部分用户频繁恶意提交收录申请，给审核团队造成不必要的麻烦
 */
@Component
class Blacklist(
    private val elasticsearchProvider: ElasticsearchProvider
) {

    private val index = "blacklist"

    init {
        initializeBlacklist()
    }

    private fun initializeBlacklist() {
        val exist = elasticsearchProvider.checkIndexExist(index)
        if (exist) return
//        if(exist) elasticsearchProvider.deleteIndex(index)
        elasticsearchProvider.createIndex(index)
    }

    enum class BlackType {
        Record,
        User
    }

    data class Black(
        val uuid: String,
        val type: BlackType,
        val displayName: String,
        val level: Int,
        val chatId: Long?,
        val username: String?,
        val unfreezeTime: Long
    )

    fun get(username: String): Black? {
        val queryBuilder = QueryBuilders.matchQuery("username", username)
        return getBlack(queryBuilder)
    }

    fun get(chatId: Long): Black? {
        val queryBuilder = QueryBuilders.matchQuery("chatId", chatId)
        return getBlack(queryBuilder)
    }

    private fun getBlack(queryBuilder: MatchQueryBuilder): Black? {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.query(queryBuilder)
        searchRequest.source(searchSourceBuilder)
        val response = elasticsearchProvider.search(searchRequest)
        return if (response.hits.totalHits!!.value < 1) null
        else generateBlackFromHashMap(response.hits.hits[0].id, response.hits.hits[0].sourceAsMap)
    }

    fun add(black: Black): Boolean {
        val builder = generateXContentFromBlack(black)
        val indexRequest = IndexRequest(index)
        indexRequest.id(black.uuid).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    fun update(black: Black): Boolean {
        val builder = generateXContentFromBlack(black)
        val updateRequest = UpdateRequest(index, black.uuid).doc(builder)
        return elasticsearchProvider.updateDocument(updateRequest)
    }

    fun delete(uuid: String) {
        val deleteRequest = DeleteRequest(index, uuid)
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    private fun generateXContentFromBlack(black: Black): XContentBuilder {
        val builder = XContentFactory.jsonBuilder()
        builder.startObject()
        builder.field("type", black.type.name)
        builder.field("displayName", black.displayName)
        builder.field("level", black.level)
        builder.field("chatId", black.chatId)
        builder.field("username", black.username)
        builder.field("unfreezeTime", black.unfreezeTime)
        builder.endObject()
        return builder
    }

    private fun generateBlackFromHashMap(uuid: String, map: MutableMap<String, Any?>): Black {
        return Black(
            uuid,
            BlackType.valueOf(map["type"] as String),
            map["displayName"] as String,
            map["level"] as Int,
            when (map["chatId"]) {
                is Int -> (map["chatId"] as Int).toLong()
                is Long -> map["chatId"] as Long
                else -> null
            },
            map["username"] as String?,
            when (map["unfreezeTime"]) {
                is Int -> (map["unfreezeTime"] as Int).toLong()
                is Long -> map["unfreezeTime"] as Long
                else -> 0L
            }
        )
    }

}