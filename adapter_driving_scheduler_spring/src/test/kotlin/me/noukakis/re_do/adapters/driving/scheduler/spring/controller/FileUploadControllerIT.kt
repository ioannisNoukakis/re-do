package me.noukakis.re_do.adapters.driving.scheduler.spring.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FileUploadControllerTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `should upload a file and return its id and storage reference`() {
        mockMvc.perform(
            multipart("/api/v1/files/upload")
                .file(MockMultipartFile("file", "report.csv", "text/csv", "col1,col2\nval1,val2".toByteArray()))
                .header("X-Auth-Principal", IDENTITY_SUB)
                .header("X-Auth-Roles", IDENTITY_ROLES)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fileId").isNotEmpty)
            .andExpect(jsonPath("$.ref").isNotEmpty)
            .andExpect(jsonPath("$.storedWith").isNotEmpty)
    }

    @Test
    fun `should return bad request when the principal header is missing`() {
        mockMvc.perform(
            multipart("/api/v1/files/upload")
                .file(MockMultipartFile("file", "report.csv", "text/csv", "content".toByteArray()))
                .header("X-Auth-Roles", IDENTITY_ROLES)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return bad request when the roles header is missing`() {
        mockMvc.perform(
            multipart("/api/v1/files/upload")
                .file(MockMultipartFile("file", "report.csv", "text/csv", "content".toByteArray()))
                .header("X-Auth-Principal", IDENTITY_SUB)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return bad request when the file part is missing`() {
        mockMvc.perform(
            multipart("/api/v1/files/upload")
                .header("X-Auth-Principal", IDENTITY_SUB)
                .header("X-Auth-Roles", IDENTITY_ROLES)
        )
            .andExpect(status().isBadRequest)
    }
}
