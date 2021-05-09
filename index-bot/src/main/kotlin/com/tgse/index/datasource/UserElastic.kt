package com.tgse.index.datasource

import com.pengrad.telegrambot.model.User
import com.tgse.index.provider.ElasticsearchProvider
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.springframework.stereotype.Component
import java.util.*


/**
 * 用于统计用户量、日活用户
 *
 * TG-ES 承诺：
 * 绝不泄露用户数据
 * 绝不收集用户查询记录
 * 绝不用作其他目的
 */
@Component
class UserElastic(
    private val elasticsearchProvider: ElasticsearchProvider
) {

    private val index = "user"

    init {
        initializeUser()
    }

    private fun initializeUser() {
        val exist = elasticsearchProvider.checkIndexExist(index)
        if (exist) return
        val request = CreateIndexRequest(index)
        request.settings(
            Settings.builder()
                .put("index.number_of_shards", 3)
                .put("index.number_of_replicas", 1)
        )
        request.mapping(
            """
            {
                "properties": {
                    "createTime": {
                        "type": "date"
                    },
                    "updateTime": {
                        "type": "date"
                    }
                }
            }
            """.trimIndent(),
            XContentType.JSON
        )
        elasticsearchProvider.createIndex(request)
    }

    data class User(
        val id: Long,
        /**
         * 首次使用时间
         */
        val createTime: Long,
        /**
         * 上次使用时间
         */
        val updateTime: Long
    )


    fun footprint(tguser:com.pengrad.telegrambot.model.User){
        val now = Date().time
        // 10分中内无需更新
        val cycle = 600000
        val exist = get(tguser.id().toLong())

        when {
            exist == null -> {
                val user = User(tguser.id().toLong(), now,now)
                add (user)
            }
            now - exist.updateTime  > cycle ->{
                val user = exist.copy(updateTime = now)
                update(user)
            }
        }
    }

    private fun get(id: Long): User? {
        val request = GetRequest(index, id.toString())
        val response = elasticsearchProvider.getDocument(request)
        if (!response.isExists) return null
        return generateUserFromHashMap(id, response.sourceAsMap)
    }

    private fun add(user: User): Boolean {
        val builder = generateXContentFromUser(user)
        val indexRequest = IndexRequest(index)
        indexRequest.id(user.id.toString()).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    private fun update(user: User): Boolean {
        val builder = generateXContentFromUser(user)
        val updateRequest = UpdateRequest(index, user.id.toString()).doc(builder)
        return elasticsearchProvider.updateDocument(updateRequest)
    }

    private fun delete(id: Long) {
        val deleteRequest = DeleteRequest(index, id.toString())
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    /**
     * 日增
     *
     * gt: > 大于（greater than）
     * lt: < 小于（less than）
     * gte: >= 大于或等于（greater than or equal to）
     * lte: <= 小于或等于（less than or equal to）
     */
    fun dailyIncrease ():Long {
        val query = QueryBuilders.rangeQuery("createTime").gte("now-24h")
        return elasticsearchProvider.countOfQuery(index, query)
    }

    /**
     * 日活
     */
    fun dailyActive():Long {
        val query = QueryBuilders.rangeQuery("update").gte("now-24h")
        return elasticsearchProvider.countOfQuery(index, query)
    }

    fun count(): Long {
        return elasticsearchProvider.countOfDocument(index)
    }

    private fun generateXContentFromUser(user: User): XContentBuilder {
        val builder = XContentFactory.jsonBuilder()
        builder.startObject()
        builder.field("createTime", user.createTime)
        builder.field("updateTime", user.updateTime)
        builder.endObject()
        return builder
    }

    private fun generateUserFromHashMap(id: Long, map: MutableMap<String, Any?>): User {
        return User(
            id,
            map["createTime"] as Long,
            map["updateTime"] as Long,
        )
    }

}