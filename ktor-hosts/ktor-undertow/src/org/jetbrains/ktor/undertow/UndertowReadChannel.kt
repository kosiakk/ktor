package org.jetbrains.ktor.undertow

import io.undertow.server.*
import org.jetbrains.ktor.cio.*
import org.xnio.channels.*
import java.nio.*
import kotlin.coroutines.experimental.intrinsics.*

class UndertowReadChannel(val channel: StreamSourceChannel) : ReadChannel {
    suspend override fun read(dst: ByteBuffer): Int {
        val read = channel.read(dst)
        if (read != 0) {
            return read
        }
        return suspendCoroutineOrReturn<Int> { continuation ->
            channel.readSetter.set { channel ->
                val count = channel.read(dst)
                continuation.resume(count)
            }
            COROUTINE_SUSPENDED
        }
    }

    override fun close() {
        channel.shutdownReads()
    }
}