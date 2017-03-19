package org.jetbrains.ktor.undertow

import io.undertow.Undertow
import org.jetbrains.ktor.host.*
import java.util.concurrent.*
import javax.net.ssl.*

class UndertowApplicationHost(environment: ApplicationHostEnvironment) : BaseApplicationHost(environment) {

    override fun start(wait: Boolean): ApplicationHost {
        environment.start()
        server.start()
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        server.stop()
        environment.stop()
    }

    private val server: Undertow = Undertow.builder().apply {
        environment.connectors.map { connector ->
            when (connector.type) {
                ConnectorType.HTTP -> addHttpListener(connector.port, connector.host)
                ConnectorType.HTTPS -> addHttpsListener(connector.port, connector.host, sslContext(connector as HostSSLConnectorConfig))
                else -> throw IllegalArgumentException("Connector type ${connector.type} is not supported by Undertow host implementation")
            }
        }

        setHandler(UndertowHttpHandler(this@UndertowApplicationHost))
    }.build()

    internal val callWorker = ForkJoinPool() // processes calls

    private fun sslContext(connector: HostSSLConnectorConfig): SSLContext {
        val keyManagers: Array<KeyManager>
        val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(connector.keyStore, connector.keyStorePassword())
        keyManagers = keyManagerFactory.keyManagers

        val trustManagers: Array<TrustManager>
        val trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(connector.keyStore)
        trustManagers = trustManagerFactory.trustManagers

        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagers, trustManagers, null)
        return sslContext
    }

    override fun toString(): String {
        return "Undertow($environment)"
    }
}

