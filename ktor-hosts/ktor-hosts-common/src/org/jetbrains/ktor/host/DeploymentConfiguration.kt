package org.jetbrains.ktor.host

/**
 * Host deployment configuration interface
 *
 * It is bound to `ktor.deployment` configuration key
 */
interface DeploymentConfiguration {
    val host: String get() = "0.0.0.0"
    val port: Int get() = 80

    val secure: SecureDeploymentConfiguration?
}

interface SecureDeploymentConfiguration {
    val port: Int
    val keyStore: String
    val keyStorePassword: String
    val keyAlias: String
    val privateKeyPassword: String
}

interface ApplicationConfiguration {
    val id get() = "Application"
    val modules: List<String> get() = emptyList()
    val watch: List<String> get() = emptyList()
}