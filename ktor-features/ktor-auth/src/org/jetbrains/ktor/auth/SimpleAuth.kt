package org.jetbrains.ktor.auth

import org.jetbrains.ktor.util.*
import java.util.*

data class UserIdPrincipal(val name: String) : Principal
data class UserPasswordCredential(val name: String, val password: String) : Credential

interface HashingConfiguration {
    val hashAlgorithm: String
    val salt: String
    val users: List<UserConfiguration>
}

interface UserConfiguration {
    val name: String
    val hash: String
}

class UserHashedTableAuth(val digester: (String) -> ByteArray = getDigestFunction("SHA-256", "ktor"),
                          val table: Map<String, ByteArray>) {

    // TODO: Use ApplicationConfig instead of HOCON
    constructor(config: HashingConfiguration) : this(getDigestFunction(
            config.hashAlgorithm,
            config.salt), config.users.associateBy({ it.name }, { decodeBase64(it.hash) }))

    init {
        if (table.isEmpty()) {
            // TODO log no users configured
        }
    }

    fun authenticate(credential: UserPasswordCredential): UserIdPrincipal? {
        val userPasswordHash = table[credential.name]
        if (userPasswordHash != null && Arrays.equals(digester(credential.password), userPasswordHash)) {
            return UserIdPrincipal(credential.name)
        }

        return null
    }
}
