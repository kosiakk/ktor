package org.jetbrains.ktor.host

import com.jdiazcano.cfg4k.hocon.*
import com.jdiazcano.cfg4k.providers.*
import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.logging.*
import java.io.*
import java.net.*
import java.security.*

/**
 * Creates an [ApplicationHostEnvironment] instance from command line arguments
 */
fun commandLineEnvironment(args: Array<String>): ApplicationHostEnvironment {
    val argsMap = args.mapNotNull { it.splitPair('=') }.toMap()

    val jar = argsMap["-jar"]?.let { File(it).toURI().toURL() }
    val configFile = argsMap["-config"]?.let { File(it) }
    val commandLineMap = argsMap.filterKeys { it.startsWith("-P:") }.mapKeys { it.key.removePrefix("-P:") }.toMutableMap()

    argsMap["-host"]?.let { commandLineMap["ktor.deployment.host"] = it }
    argsMap["-port"]?.let { commandLineMap["ktor.deployment.port"] = it }
    argsMap["-watch"]?.let { commandLineMap["ktor.application.watch"] = it }

    val environmentConfig = ConfigFactory.systemProperties().withOnlyPath("ktor")
    val fileConfig = configFile?.let { ConfigFactory.parseFile(it) } ?: ConfigFactory.load()
    val argConfig = ConfigFactory.parseMap(commandLineMap, "Command-line options")
    val combinedConfig = argConfig.withFallback(fileConfig).withFallback(environmentConfig)

    val provider = Providers.proxy(HoconConfigLoader(combinedConfig))
    val deployment = provider.bind<DeploymentConfiguration>("ktor.deployment")
    val application = provider.bind<ApplicationConfiguration>("ktor.application")

    val appLog = SLF4JApplicationLog(application.id)
    if (configFile != null && !configFile.exists()) {
        appLog.error("Configuration file '$configFile' specified as command line argument was not found")
        appLog.warning("Will attempt to start without loading configuration…")
    }

    val environment = applicationHostEnvironment {
        log = appLog
        classLoader = jar?.let { URLClassLoader(arrayOf(jar), ApplicationEnvironment::class.java.classLoader) }
                ?: ApplicationEnvironment::class.java.classLoader

        configProvider = provider

        val contentHiddenValue = ConfigValueFactory.fromAnyRef("***", "Content hidden")
        log.trace(combinedConfig.getObject("ktor")
                .withoutKey("security")
                .withValue("security", contentHiddenValue)
                .render())

        connector {
            this.host = deployment.host
            this.port = deployment.port
        }

        val secure = deployment.secure
        if (secure != null) {
            val sslPort = secure.port
            val sslKeyStorePath = secure.keyStore
            val sslKeyStorePassword = secure.keyStorePassword
            val sslPrivateKeyPassword = secure.privateKeyPassword
            val sslKeyAlias = secure.keyAlias

            val keyStoreFile = File(sslKeyStorePath).let { file ->
                if (file.exists() || file.isAbsolute)
                    file
                else
                    File(".", sslKeyStorePath).absoluteFile
            }
            val keyStore = KeyStore.getInstance("JKS").apply {
                FileInputStream(keyStoreFile).use {
                    load(it, sslKeyStorePassword.toCharArray())
                }

                requireNotNull(getKey(sslKeyAlias, sslPrivateKeyPassword.toCharArray()) == null) {
                    "The specified key $sslKeyAlias doesn't exist in the key store $sslKeyStorePath"
                }
            }

            sslConnector(keyStore, sslKeyAlias,
                    { sslKeyStorePassword.toCharArray() },
                    { sslPrivateKeyPassword.toCharArray() }) {
                this.host = deployment.host
                this.port = sslPort.toInt()
                this.keyStorePath = keyStoreFile
            }
        }
    }

    return environment
}

private fun String.splitPair(ch: Char): Pair<String, String>? = indexOf(ch).let { idx ->
    when (idx) {
        -1 -> null
        else -> Pair(take(idx), drop(idx + 1))
    }
}

