package com.tgse.index.provider

import org.apache.http.HttpHost
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.delete.DeleteResponse
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentType
import org.springframework.stereotype.Component

@Component
class ElasticsearchProvider : AutoCloseable {

    val enrollIndexName = "enroll"
    val recordIndexName = "record"
    private val client = RestHighLevelClient(
        RestClient.builder(
            HttpHost("localhost", 9200, "http"),
            HttpHost("localhost", 9201, "http")
        )
    )

    init {
        initializeEnroll()
//        initializeRecord()
    }

    private fun initializeEnroll() {
        val exist = checkIndexExist(enrollIndexName)
        if (exist) return
        createIndex(enrollIndexName)
    }

    private fun initializeRecord() {
        val exist = checkIndexExist(recordIndexName)
        if (exist) return
//        if (exist) deleteIndex(indexName)
        val request = CreateIndexRequest("")

        request.settings(
            Settings.builder()
                .put("index.number_of_shards", 3)
                .put("index.number_of_replicas", 2)
        )

        request.mapping(
            """
            {
                "properties":{
                    "type":{
                        "type":"keyword"
                    },
                    "label":{
                        "type":"text"
                    },
                    "sort":{
                        "type":"short"
                    },
                }
            }
            """.trimIndent(),
            XContentType.JSON
        )

        val response = client.indices().create(request, RequestOptions.DEFAULT)
    }


    /**
     * 检查索引是否存在
     */
    private fun checkIndexExist(indexName: String): Boolean {
        val getRequest = GetIndexRequest(indexName)
        return client.indices().exists(getRequest, RequestOptions.DEFAULT)
    }

    /**
     * 创建索引
     */
    private fun createIndex(indexName: String): Boolean {
        val createRequest = CreateIndexRequest(indexName)
        val response = client.indices().create(createRequest, RequestOptions.DEFAULT)
        return response.isAcknowledged
    }

    /**
     * 创建索引
     */
    private fun createIndex(createRequest: CreateIndexRequest): Boolean {
        val response = client.indices().create(createRequest, RequestOptions.DEFAULT)
        return response.isAcknowledged
    }

    /**
     * 删除索引
     */
    private fun deleteIndex(indexName: String): Boolean {
        val deleteRequest = DeleteIndexRequest(indexName)
        val response = client.indices().delete(deleteRequest, RequestOptions.DEFAULT)
        return response.isAcknowledged
    }

    /**
     * 索引文档
     */
    fun indexDocument(request: IndexRequest): Boolean {
        return try {
            client.index(request, RequestOptions.DEFAULT)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 更新文档
     */
    fun updateDocument(request: UpdateRequest): Boolean {
        return try {
            client.update(request, RequestOptions.DEFAULT)
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 获取文档
     */
    fun getDocument(request: GetRequest): GetResponse {
        return client.get(request, RequestOptions.DEFAULT)
    }

    /**
     * 删除文档
     */
    fun deleteDocument(request: DeleteRequest): DeleteResponse {
        return client.delete(request, RequestOptions.DEFAULT)
    }

    override fun close() {
        client.close()
    }

}