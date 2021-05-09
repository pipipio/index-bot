package com.tgse.index.datasource

import org.dom4j.Document
import org.dom4j.Element
import org.dom4j.io.SAXReader
import org.springframework.stereotype.Component
import java.io.FileOutputStream
import java.nio.file.Path

/**
 * 分类
 */
@Component
class Type {

    private val path = Path.of("src/main/resources/data/type.xml")
    private lateinit var typesOnDisk: MutableList<String>
    val types: Array<String>
        get() = typesOnDisk.toTypedArray()

    init {
        read()
    }

    fun add(type: String): Boolean {
        if (typesOnDisk.contains(type)) return true
        typesOnDisk.add(type)
        write()
        return true
    }

    fun remove(type: String): Boolean {
        if (!typesOnDisk.contains(type)) return true
        typesOnDisk.remove(type)
        write()
        return true
    }

    fun contains(type: String): Boolean {
        return typesOnDisk.contains(type)
    }

    private fun write() {
        val outputStream = FileOutputStream(path.toFile())
        outputStream.writer(charset("UTF-8")).use {
            it.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <type>
                    <tp>
                        ${typesOnDisk.joinToString(",")}
                    </tp>
                </type>
                """.trimIndent()
            )
        }
    }

    private fun read() {
        val reader = SAXReader()
        val doc: Document = reader.read(path.toFile())
        val root: Element = doc.rootElement
        val it: Iterator<Element> = root.elementIterator()
        while (it.hasNext()) {
            val e: Element = it.next()
            typesOnDisk = e.textTrim.split(",").toMutableList()
        }
    }

}