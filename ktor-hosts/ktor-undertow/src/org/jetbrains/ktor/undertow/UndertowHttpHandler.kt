package org.jetbrains.ktor.undertow

import io.undertow.server.*
import kotlinx.coroutines.experimental.*
import kotlin.coroutines.experimental.*

class UndertowHttpHandler(val host: UndertowApplicationHost) : HttpHandler {
    override fun handleRequest(exchange: HttpServerExchange) {
        if (exchange.isInIoThread) {
            exchange.dispatch(host.callWorker, this)
            return
        }

        launch(UndertowCoroutineDispatcher(exchange)) {
            try {
                val call = UndertowApplicationCall(host.application, exchange)
                host.pipeline.execute(call, Unit)
            } catch (t: Throwable) {
                host.environment.log.error(t)
            }
            exchange.endExchange()
        }
    }

    private class UndertowCoroutineDispatcher(val exchange: HttpServerExchange) : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable): Unit {
            exchange.dispatch(block)
        }
    }

}