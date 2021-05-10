package com.tgse.index.datasource

import org.dom4j.Document
import org.dom4j.Element
import org.dom4j.io.SAXReader
import org.springframework.stereotype.Component
import java.io.File

/**
 * 回执信息
 */
@Component
class Reply {

    private var messageOnDisk = mutableMapOf<String, String>()
    val message: Map<String, String>
        get() = messageOnDisk.toMap()

    init {
        // 读取xml文件内容
        val reader = SAXReader()
        val doc: Document = reader.read(File("lang/reply.xml"))
        val root: Element = doc.getRootElement()
        val it: Iterator<Element> = root.elementIterator()
        while (it.hasNext()) {
            val e: Element = it.next()
            val type = e.elementTextTrim("type")
            val content = e.elementText("content").replaceFirst("\n", "").replace("    ", "")
            messageOnDisk[type] = content
        }
    }

}