package org.jetbrains.ktor.undertow

import io.undertow.server.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*

class UndertowApplicationCall(application: Application, val exchange: HttpServerExchange) : BaseApplicationCall(application) {
    override val request = UndertowApplicationRequest(this)
    override val response = UndertowApplicationResponse(this)

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {}

    override suspend fun responseChannel() = UndertowWriteChannel(exchange.responseSender)
}