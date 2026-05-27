package me.noukakis.re_do.adapters.driving.scheduler.spring.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(
    properties = [
        "scheduler.auth.mode=oidc",
        "scheduler.auth.oidc.issuer-uri=https://issuer.invalid/",
        "scheduler.auth.oidc.roles-claim=roles",
        "scheduler.auth.required-roles=scheduler-user",
        "spring.main.allow-bean-definition-overriding=true",
    ],
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FileUploadControllerOidcIT {

    @TestConfiguration
    class StubJwtDecoderConfig {
        @Bean
        @Primary
        fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withJwkSetUri("https://issuer.invalid/jwks").build()
    }

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    private val file get() = MockMultipartFile("file", "report.csv", "text/csv", "col1,col2\nval1,val2".toByteArray())

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
            .build()
    }

    @Test
    fun `rejects upload with 401 when no token is provided`() {
        mockMvc.perform(multipart("/api/v1/files/upload").file(file))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `rejects upload with 403 when token is missing required role`() {
        mockMvc.perform(
            multipart("/api/v1/files/upload")
                .file(file)
                .with(jwt().jwt { it.subject("alice").claim("roles", listOf("other-role")) }),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `accepts upload when token carries required role`() {
        mockMvc.perform(
            multipart("/api/v1/files/upload")
                .file(file)
                .with(jwt().jwt { it.subject("alice").claim("roles", listOf("scheduler-user")) }),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ref").isNotEmpty)
            .andExpect(jsonPath("$.storedWith").isNotEmpty)
    }
}
