package org.jetbrains.ktor.util

import com.typesafe.config.*

fun Config.tryGetString(path: String) = if (hasPath(path)) getString(path) else null
fun Config.tryGetStringList(path: String) = if (hasPath(path)) getStringList(path) else null
