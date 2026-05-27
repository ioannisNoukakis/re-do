package me.noukakis.re_do.adapters.driving.scheduler.spring.configuration

import me.noukakis.re_do.adapters.driving.scheduler.spring.error.ApiError
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper

@Configuration
class SecurityConfiguration(
    private val authProperties: AuthProperties,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }

        when (authProperties.mode) {
            AuthMode.DISABLED, AuthMode.HEADERS -> {
                http.authorizeHttpRequests { it.anyRequest().permitAll() }
            }

            AuthMode.OIDC -> {
                http
                    .authorizeHttpRequests {
                        it
                            .requestMatchers(
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                            ).permitAll()
                            .anyRequest().authenticated()
                    }
                    .oauth2ResourceServer { rs ->
                        rs
                            .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                            .accessDeniedHandler(jsonAccessDeniedHandler())
                            .jwt { }
                    }
                    .exceptionHandling {
                        it
                            .authenticationEntryPoint(jsonAuthenticationEntryPoint())
                            .accessDeniedHandler(jsonAccessDeniedHandler())
                    }
            }
        }
        return http.build()
    }

    @Bean
    @ConditionalOnProperty(name = ["scheduler.auth.mode"], havingValue = "oidc")
    fun jwtDecoder(): JwtDecoder {
        require(authProperties.oidc.issuerUri.isNotBlank()) {
            "scheduler.auth.oidc.issuer-uri must be set when scheduler.auth.mode=oidc"
        }
        val decoder = NimbusJwtDecoder.withIssuerLocation(authProperties.oidc.issuerUri).build()
        val validators = mutableListOf<OAuth2TokenValidator<Jwt>>(
            JwtValidators.createDefaultWithIssuer(authProperties.oidc.issuerUri),
        )
        if (authProperties.oidc.audience.isNotBlank()) {
            validators += audienceValidator(authProperties.oidc.audience)
        }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
        return decoder
    }

    private fun audienceValidator(expected: String): OAuth2TokenValidator<Jwt> = OAuth2TokenValidator { jwt ->
        if (jwt.audience.contains(expected)) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(
                OAuth2Error("invalid_token", "JWT 'aud' does not contain expected audience '$expected'", null),
            )
        }
    }

    private fun jsonAuthenticationEntryPoint() = org.springframework.security.web.AuthenticationEntryPoint { _, response, ex ->
        response.status = 401
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, ApiError("Authentication required: ${ex.message}"))
    }

    private fun jsonAccessDeniedHandler() = org.springframework.security.web.access.AccessDeniedHandler { _, response, ex ->
        response.status = 403
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        objectMapper.writeValue(response.writer, ApiError("Access denied: ${ex.message}"))
    }
}
