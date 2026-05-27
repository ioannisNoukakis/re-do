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

@SpringBootTest(
    properties = [
        "scheduler.auth.mode=disabled",
        "scheduler.auth.default-principal=anon",
        "scheduler.auth.default-roles=guest",
    ],
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FileUploadControllerAuthDisabledIT {

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build()
    }

    @Test
    fun `should accept upload without auth headers when verify-headers is disabled`() {
        mockMvc.perform(
            multipart("/api/v1/files/upload")
                .file(MockMultipartFile("file", "report.csv", "text/csv", "col1,col2\nval1,val2".toByteArray())),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ref").isNotEmpty)
            .andExpect(jsonPath("$.storedWith").isNotEmpty)
    }
}
