package com.tgse.index.setting

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import kotlin.properties.Delegates

@Configuration
@ConfigurationProperties(prefix = "elastic")
class ElasticProperties {
    var schema by Delegates.notNull<String>()
    var hostname by Delegates.notNull<String>()
    var port by Delegates.notNull<Int>()
}