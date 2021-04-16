package com.tgse.index.setting

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import kotlin.properties.Delegates

@Configuration
@ConfigurationProperties(prefix = "bot")
class BotProperties {
    var creator by Delegates.notNull<String>()
    var token by Delegates.notNull<String>()
}