package org.jetbrains.ktor.undertow

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

class UndertowApplicationRequest(override val call: UndertowApplicationCall) : BaseApplicationRequest() {

    override fun receiveContent() = UndertowIncomingContent(this)

    override val local = UndertowConnectionPoint(call.exchange)
    override val cookies = UndertowRequestCookies(this)

    override val headers by lazy {
        ValuesMap.build(caseInsensitiveKey = true) {
            val headers = call.exchange.requestHeaders
            for (it in headers) {
                appendAll(it.headerName.toString(), it)
            }
        }
    }

    override val queryParameters by lazy {
        parseQueryString(call.exchange.queryString)
    }

}

class UndertowIncomingContent(override val request: UndertowApplicationRequest) : IncomingContent {
    override fun readChannel(): ReadChannel {
        return UndertowReadChannel(request.call.exchange.requestChannel)
    }

    override fun multiPartData(): MultiPartData {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class UndertowRequestCookies(request: ApplicationRequest) : RequestCookies(request) {
    // TODO: get cookies from undertow if they are already processed
}