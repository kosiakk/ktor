package org.jetbrains.ktor.undertow

import org.jetbrains.ktor.host.*

object Undertow : ApplicationHostFactory<UndertowApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = UndertowApplicationHost(environment)
}

