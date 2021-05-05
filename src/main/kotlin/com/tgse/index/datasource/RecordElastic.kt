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
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

/**
 * 数据引擎
 */
@Component
class RecordElastic(
    private val elasticsearchProvider: ElasticsearchProvider,
    private val telegram: Telegram
) {

    private val index = "record"

    init {
        initializeRecord()
    }

    private fun initializeRecord() {
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
                    "bulletinMessageId": {
                        "type": "integer"
                    },
                    "type": {
                        "type": "keyword"
                    },
                    "chatId": {
                        "type": "long"
                    },
                    "title": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "search_analyzer": "ik_max_word"
                    },
                    "description": {
                        "type": "text"
                    },
                    "tags": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "search_analyzer": "ik_max_word"
                    },
                    "classification": {
                        "type": "text",
                        "analyzer": "ik_max_word",
                        "search_analyzer": "ik_max_word"
                    },
                    "username": {
                        "type": "text"
                    },
                    "link": {
                        "type": "text"
                    },
                    "members": {
                        "type": "long"
                    },
                    "createTime": {
                        "type": "date"
                    },
                    "createUser": {
                        "type": "text"
                    },
                    "createUserName": {
                        "type": "text"
                    }
                }
            }
            """.trimIndent(),
            XContentType.JSON
        )
        elasticsearchProvider.createIndex(request)
    }

    data class Record(
        val uuid: String,
        val bulletinMessageId: Int?,
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
        val updateTime: Long,
    )

    private val updateRecordSubject = BehaviorSubject.create<Record>()
    val updateRecordObservable: Observable<Record> = updateRecordSubject.distinct()

    private val deleteRecordSubject = BehaviorSubject.create<Pair<Record, User>>()
    val deleteRecordObservable: Observable<Pair<Record, User>> = deleteRecordSubject.distinct()

    /**
     * 根据关键词查询
     */
    fun searchRecords(keyword: String, from: Int, size: Int): Pair<MutableList<Record>, Long> {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        val queryBuilder = QueryBuilders.multiMatchQuery(keyword, "title", "tags", "classification")
        searchSourceBuilder.query(queryBuilder).from(from).size(size).sort("members", SortOrder.DESC)
        searchRequest.source(searchSourceBuilder)
        val response = elasticsearchProvider.search(searchRequest)

        val records = arrayListOf<Record>()
        response.hits.hits.forEach {
            val record = generateRecordFromHashMap(it.id, it.sourceAsMap)
            records.add(record)
        }
        val totalCount = response.hits.totalHits?.value ?: 0L
        return Pair(records.toMutableList(), totalCount)
    }

    /**
     * 根据提交者查询
     */
    fun searchRecords(user: User, from: Int, size: Int): Pair<MutableList<Record>, Long> {
        try {
            val searchRequest = SearchRequest(index)
            val searchSourceBuilder = SearchSourceBuilder()
            val queryBuilder = QueryBuilders.matchQuery("createUser", user.id())
            searchSourceBuilder.query(queryBuilder).from(from).size(size).sort("createTime", SortOrder.DESC)
            searchRequest.source(searchSourceBuilder)
            val response = elasticsearchProvider.search(searchRequest)

            val records = arrayListOf<Record>()
            response.hits.hits.forEach {
                val record = generateRecordFromHashMap(it.id, it.sourceAsMap)
                records.add(record)
            }
            val totalCount = response.hits.totalHits?.value ?: 0L
            return Pair(records.toMutableList(), totalCount)
        } catch (e: Throwable) {
            return Pair(mutableListOf(), 0L)
        }
    }

    fun addRecord(record: Record): Boolean {
        val builder = generateXContentFromRecord(record)
        val indexRequest = IndexRequest(index)
        indexRequest.id(record.uuid).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    fun updateRecord(record: Record) {
        val newRecord = record.copy(updateTime = Date().time)
        val builder = generateXContentFromRecord(newRecord)
        val updateRequest = UpdateRequest(index, newRecord.uuid).doc(builder)
        elasticsearchProvider.updateDocument(updateRequest)
        updateRecordSubject.onNext(newRecord)
    }

    fun deleteRecord(uuid: String, manager: User) {
        val record = getRecord(uuid)!!
        val deleteRequest = DeleteRequest(index, uuid)
        elasticsearchProvider.deleteDocument(deleteRequest)
        deleteRecordSubject.onNext(Pair(record, manager))
    }

    fun getRecord(uuid: String): Record? {
        val request = GetRequest(index, uuid)
        val response = elasticsearchProvider.getDocument(request)
        if (!response.isExists) return null
        val record = generateRecordFromHashMap(uuid, response.sourceAsMap)
        return realTimeRecord(record)
    }

    fun getRecordByUsername(username: String): Record? {
        val queryBuilder = QueryBuilders.matchQuery("username", username)
        return getRecord(queryBuilder)
    }

    fun getRecordByChatId(chatId: Long): Record? {
        val queryBuilder = QueryBuilders.matchQuery("chatId", chatId)
        return getRecord(queryBuilder)
    }

    private fun getRecord(queryBuilder: MatchQueryBuilder): Record? {
        val searchRequest = SearchRequest(index)
        val searchSourceBuilder = SearchSourceBuilder()
        searchSourceBuilder.query(queryBuilder)
        searchRequest.source(searchSourceBuilder)
        val response = elasticsearchProvider.search(searchRequest)
        return if (response.hits.totalHits!!.value < 1) null
        else generateRecordFromHashMap(response.hits.hits[0].id, response.hits.hits[0].sourceAsMap)
    }

    fun count(): Long {
        return elasticsearchProvider.countOfDocument(index)
    }

    private fun realTimeRecord(record: Record): Record {
        try {
            // 24小时后更新人数信息
            val isOver24Hours = record.updateTime < Date().time - 24 * 60 * 60 * 1000
            val isHasMembers = record.members != null
            return if (isOver24Hours && isHasMembers) {
                val telegramMod =
                    if (record.username != null) telegram.getTelegramModFromWeb(record.username)
                    else telegram.getTelegramGroupFromChat(record.chatId!!)

                val members = when (telegramMod) {
                    is Telegram.TelegramChannel -> telegramMod.members
                    is Telegram.TelegramGroup -> telegramMod.members
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

    private fun generateXContentFromRecord(record: Record): XContentBuilder {
        val tagsString =
            if (record.tags == null) null
            else record.tags.joinToString(" ")
        val builder = XContentFactory.jsonBuilder()
        builder.startObject()
        builder.field("bulletinMessageId", record.bulletinMessageId)
        builder.field("type", record.type.name)
        builder.field("chatId", record.chatId)
        builder.field("title", record.title)
        builder.field("description", record.description)
        builder.field("tags", tagsString)
        builder.field("classification", record.classification)
        builder.field("username", record.username)
        builder.field("link", record.link)
        builder.field("members", record.members)
        builder.field("createTime", record.createTime)
        builder.field("createUser", record.createUser)
        builder.field("updateTime", record.updateTime)
        builder.endObject()
        return builder
    }

    private fun generateRecordFromHashMap(uuid: String, map: MutableMap<String, Any?>): Record {
        val tagsString = map["tags"].toString()
        val tags = when {
            tagsString.contains(" ") -> tagsString.split(" ")
            tagsString == "null" -> null
            else -> listOf(tagsString)
        }
        return Record(
            uuid,
            map["bulletinMessageId"] as Int,
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
            map["updateTime"] as Long
        )
    }

}