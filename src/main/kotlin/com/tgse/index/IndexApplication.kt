package com.tgse.index

import com.pengrad.telegrambot.model.User
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class IndexApplication

fun main(args: Array<String>) {
    runApplication<IndexApplication>(*args)
}

fun User.nick(): String {
    val firstName =
        if (firstName() == null) ""
        else firstName()
    val lastName =
        if (lastName() == null) ""
        else lastName()
    return "$firstName$lastName"
}