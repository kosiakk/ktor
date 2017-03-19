package org.jetbrains.ktor.undertow.tests

import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.undertow.*

class UndertowHostTest : HostTestSuite<UndertowApplicationHost>(Undertow)