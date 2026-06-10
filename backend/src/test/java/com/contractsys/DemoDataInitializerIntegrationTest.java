package com.contractsys;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.demo-data.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:contractsys_demo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class DemoDataInitializerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void demoDataCanBeSeededFromStartupSwitch() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"demo_manager","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("demo_manager"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(loginResponse).path("data").path("token").asText();
        mockMvc.perform(get("/api/v1/contracts/query")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "演示采购合同"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("演示采购合同"));
    }
}
