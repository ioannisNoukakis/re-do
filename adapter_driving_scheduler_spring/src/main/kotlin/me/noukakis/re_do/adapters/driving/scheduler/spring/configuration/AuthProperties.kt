package me.noukakis.re_do.adapters.driving.scheduler.spring.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

enum class AuthMode { DISABLED, HEADERS, OIDC }

@ConfigurationProperties(prefix = "scheduler.auth")
data class AuthProperties(
    val mode: AuthMode = AuthMode.HEADERS,
    val defaultPrincipal: String = "anonymous",
    val defaultRoles: List<String> = emptyList(),
    val requiredRoles: List<String> = emptyList(),
    val oidc: OidcProperties = OidcProperties(),
) {
    data class OidcProperties(
        val issuerUri: String = "",
        val audience: String = "",
        val rolesClaim: String = "roles",
        val principalClaim: String = "sub",
    )
}
