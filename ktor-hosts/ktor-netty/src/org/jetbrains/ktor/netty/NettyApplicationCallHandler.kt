package org.jetbrains.ktor.netty

import io.netty.channel.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.pipeline.*
import kotlin.coroutines.experimental.*

internal class NettyApplicationCallHandler(private val userCoroutineContext: CoroutineContext,
                                           private val hostPipeline: HostPipeline) : ChannelInboundHandlerAdapter() {
    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        when (msg) {
            is ApplicationCall -> handleRequest(ctx, msg)
            else -> ctx.fireChannelRead(msg)
        }
    }

    private fun handleRequest(context: ChannelHandlerContext, call: ApplicationCall) {
        launch(userCoroutineContext + NettyDispatcher.CurrentContext(context)) {
            hostPipeline.execute(call)
        }
    }
}