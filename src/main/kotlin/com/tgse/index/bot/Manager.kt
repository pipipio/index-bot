package com.tgse.index.bot

import com.tgse.index.provider.BotProvider
import com.tgse.index.provider.WatershedProvider
import org.springframework.stereotype.Service

@Service
class Manager(
    private val botProvider: BotProvider,
    private val watershedProvider: WatershedProvider
) {

    init {
        listen()
    }

    private fun listen() {
        watershedProvider.requestObservable.subscribe(
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