package me.noukakis.re_do.adapters.driving.scheduler.spring.controller

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(properties = ["scheduler.auth.mode=headers"])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FileUploadControllerHeadersIT {

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    private val file get() = MockMultipartFile("file", "report.csv", "text/csv", "col1,col2".toByteArray())

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `rejects upload when X-Auth-Principal header is missing`() {
        mockMvc.perform(multipart("/api/v1/files/upload").file(file))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `accepts upload when X-Auth-Principal and X-Auth-Roles headers are present`() {
        mockMvc.perform(
            multipart("/api/v1/files/upload")
                .file(file)
                .header("X-Auth-Principal", "alice")
                .header("X-Auth-Roles", "scheduler-user"),
        )
            .andExpect(status().isOk)
    }
}
