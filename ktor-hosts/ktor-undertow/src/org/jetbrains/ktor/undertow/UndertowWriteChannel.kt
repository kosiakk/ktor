package org.jetbrains.ktor.undertow

import io.undertow.io.*
import io.undertow.server.*
import org.jetbrains.ktor.cio.*
import java.io.*
import java.nio.*
import kotlin.coroutines.experimental.*

class UndertowWriteChannel(val sender: Sender) : WriteChannel {
    suspend override fun write(src: ByteBuffer) {
        suspendCoroutine<Unit> { continuation ->
            sender.send(src, object : IoCallback {
                override fun onComplete(exchange: HttpServerExchange, sender: Sender) {
                    continuation.resume(Unit)
                }

                override fun onException(exchange: HttpServerExchange, sender: Sender, exception: IOException) {
                    continuation.resumeWithException(exception)
                }
            })
        }
    }

    suspend override fun flush() {
    }

    override fun close() {
        sender.close()
    }
}