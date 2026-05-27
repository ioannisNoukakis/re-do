package me.noukakis.re_do.adapters.driving.scheduler.spring.configuration

import jakarta.servlet.http.HttpServletRequest
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions.InsufficientRoleException
import me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions.MissingAuthHeaderException
import me.noukakis.re_do.common.model.Identity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class AuthIdentityResolver(
    private val props: AuthProperties,
    private val request: HttpServletRequest,
) {
    fun resolve(): Identity {
        val identity = when (props.mode) {
            AuthMode.DISABLED -> Identity(props.defaultPrincipal, props.defaultRoles)
            AuthMode.HEADERS -> resolveFromHeaders()
            AuthMode.OIDC -> resolveFromJwt()
        }
        enforceRequiredRoles(identity)
        return identity
    }

    private fun resolveFromHeaders(): Identity {
        val sub = request.getHeader("X-Auth-Principal")
        if (sub.isNullOrBlank()) throw MissingAuthHeaderException("X-Auth-Principal")
        val roles = request.getHeaders("X-Auth-Roles").toList()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return Identity(sub, roles)
    }

    private fun resolveFromJwt(): Identity {
        val auth = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: throw MissingAuthHeaderException("Authorization")
        val jwt = auth.token
        val sub = jwt.getClaimAsString(props.oidc.principalClaim)
            ?: throw MissingAuthHeaderException("jwt.${props.oidc.principalClaim}")
        return Identity(sub, extractRoles(jwt, props.oidc.rolesClaim))
    }

    private fun enforceRequiredRoles(identity: Identity) {
        if (props.requiredRoles.isEmpty()) return
        if (identity.roles.any { it in props.requiredRoles }) return
        throw InsufficientRoleException(props.requiredRoles)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractRoles(jwt: Jwt, path: String): List<String> {
        var node: Any? = jwt.claims
        for (segment in path.split(".")) {
            node = (node as? Map<String, Any?>)?.get(segment) ?: return emptyList()
        }
        return when (node) {
            is Collection<*> -> node.filterIsInstance<String>()
            is String -> node.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }
}
