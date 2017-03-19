package org.jetbrains.ktor.undertow

import io.undertow.server.*
import org.jetbrains.ktor.http.*

class UndertowConnectionPoint(val exchange: HttpServerExchange) : RequestConnectionPoint {
    override val scheme: String
        get() = exchange.requestScheme
    override val version: String
        get() = exchange.protocol.toString()
    override val port: Int
        get() = exchange.hostPort
    override val host: String
        get() = exchange.hostName

    override val uri: String = exchange.queryString.let { query ->
        if (query == null)
            exchange.requestURI
        else
            "${exchange.requestURI}?$query"

    }

    override val method: HttpMethod
        get() = HttpMethod.parse(exchange.requestMethod.toString())
    override val remoteHost: String
        get() = exchange.connection.peerAddress.toString()
}