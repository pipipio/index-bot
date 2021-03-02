package com.scomarlf.index.bot

import com.scomarlf.index.provider.BotProvider
import org.springframework.stereotype.Service

@Service
class Manager(
    private val botProvider: BotProvider
) {

    init {
        listen()
    }

    fun listen(){

        botProvider.updateObservable.subscribe(
            { update ->
                println("Manager.listen.next")



            },
            { throwable ->
                println("Manager.listen.error")
            },
            {
                println("Manager.listen.complate")
            }
        )
    }

}