package com.scomarlf.index.datasource

import com.scomarlf.index.provider.ElasticsearchProvider
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.common.xcontent.XContentBuilder
import org.elasticsearch.common.xcontent.XContentFactory
import org.springframework.stereotype.Component

@Component
class Elasticsearch(
    private val elasticsearchProvider: ElasticsearchProvider
) {
    data class Enroll(
        val id: String,
        val title: String,
        val about: String,
        val tags: Collection<String>?,
        val classification: String?,
        val code: String?,
        val link: String?,
        val createTime: Long,
        val createUser: Long,
        val createUserName: String,
        val status: Boolean
    )


    init {
    }

    fun addEnroll(enroll: Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val indexRequest = IndexRequest(elasticsearchProvider.enrollIndexName)
        indexRequest.id(enroll.id).source(builder)
        return elasticsearchProvider.indexDocument(indexRequest)
    }

    fun updateEnroll(enroll: Enroll): Boolean {
        val builder = generateXContentFromEnroll(enroll)
        val updateRequest = UpdateRequest(elasticsearchProvider.enrollIndexName,enroll.id).doc(builder)
        return elasticsearchProvider.updateDocument(updateRequest)
    }

    fun deleteEnroll(id: String) {
        val deleteRequest = DeleteRequest(elasticsearchProvider.enrollIndexName, id)
        elasticsearchProvider.deleteDocument(deleteRequest)
    }

    fun getEnroll(id: String): Enroll? {
        val request = GetRequest(elasticsearchProvider.enrollIndexName, id)
        val response = elasticsearchProvider.getDocument(request)
        if (!response.isExists) return null
        val content = response.sourceAsMap
        val tagsString = content["tags"].toString()
        val tags = when (true) {
            tagsString.contains(" ") -> tagsString.split(" ")
            tagsString == "null" -> null
            else -> listOf(tagsString)
        }

        return Enroll(
            id,
            content["title"] as String,
            content["about"] as String,
            tags ,
            content["classification"] as String?,
            content["code"] as String?,
            content["link"] as String?,
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
        builder.field("title", enroll.title)
        builder.field("about", enroll.about)
        builder.field("tag", tagsString)
        builder.field("classification", enroll.classification)
        builder.field("code", enroll.code)
        builder.field("link", enroll.link)
        builder.field("createTime", enroll.createTime)
        builder.field("createUser", enroll.createUser)
        builder.field("createUserName", enroll.createUserName)
        builder.field("status", enroll.status)
        builder.endObject()
        return builder
    }


}