package org.jetbrains.ktor.undertow

import io.undertow.util.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*

class UndertowApplicationResponse(override val call: UndertowApplicationCall) : BaseApplicationResponse(call) {
    @Volatile
    private var responseMessageSent = false

    override fun setStatus(statusCode: HttpStatusCode) {
        call.exchange.statusCode = statusCode.value
        call.exchange.reasonPhrase = statusCode.description
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            if (responseMessageSent)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            call.exchange.responseHeaders.add(HttpString.tryFromString(name), value)
        }

        override fun getHostHeaderNames(): List<String> {
            return call.exchange.responseHeaders.headerNames.map { it.toString() }
        }

        override fun getHostHeaderValues(name: String): List<String> {
            return call.exchange.responseHeaders.eachValue(HttpString.tryFromString(name)).map { it.toString() }
        }
    }
}