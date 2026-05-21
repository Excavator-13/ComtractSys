package com.contractsys;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BoundaryIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "123456");
    }

    @Test
    void registerRejectsMismatchedPasswords() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"boundary_mismatch","password":"123456","confirmPassword":"654321"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void registerRejectsDuplicateUsername() throws Exception {
        String username = unique("boundary_dup");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"username":"%s","password":"123456","confirmPassword":"123456"}
                                """, username)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"username":"%s","password":"123456","confirmPassword":"123456"}
                                """, username)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void registerRejectsOverlongUsername() throws Exception {
        String username = "u".repeat(41);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"username":"%s","password":"123456","confirmPassword":"123456"}
                                """, username)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void loginRejectsBadPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void lowercaseBearerPrefixIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void unsupportedHttpMethodReturns405() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(40500));
    }

    @Test
    void corsOnlyAllowsConfiguredOrigins() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingUserRequiresUsernameAndPassword() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"缺少用户名和密码"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void creatingCustomerRequiresRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"边界客户"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void creatingRoleRejectsDuplicateRoleCode() throws Exception {
        String roleCode = "ROLE_BOUNDARY_" + System.nanoTime();
        String payload = String.format("""
                {"roleCode":"%s","roleName":"边界角色","description":"test"}
                """, roleCode);

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void contractListRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/v1/contracts")
                        .header("Authorization", bearer())
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void listEndpointsNormalizeNonPositivePagination() throws Exception {
        mockMvc.perform(get("/api/v1/contracts")
                        .header("Authorization", bearer())
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", bearer())
                        .param("page", "-1")
                        .param("size", "-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void creatingContractRejectsEndDateBeforeBeginDate() throws Exception {
        Long customerId = createCustomer();
        mockMvc.perform(post("/api/v1/contracts")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractPayload(customerId, LocalDate.now(), LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void creatingContractRejectsUnknownCustomer() throws Exception {
        mockMvc.perform(post("/api/v1/contracts")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractPayload(999_999_999L, LocalDate.now(), LocalDate.now().plusDays(1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void assigningContractRejectsUnknownUser() throws Exception {
        Long customerId = createCustomer();
        Long contractId = createContract(customerId);
        Long adminUserId = currentUserId();

        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/assign")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"countersignUserIds":[999999999],"approvalUserIds":[%d],"signUserIds":[%d]}
                                """, adminUserId, adminUserId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void uploadRejectsUnsupportedExtensionAndEmptyFile() throws Exception {
        Long customerId = createCustomer();
        Long contractId = createContract(customerId);
        MockMultipartFile unsupported = new MockMultipartFile(
                "file", "bad.exe", "application/octet-stream", "x".getBytes());
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/v1/contracts/" + contractId + "/attachments")
                        .file(unsupported)
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(multipart("/api/v1/contracts/" + contractId + "/attachments")
                        .file(empty)
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void uploadRejectsContentThatDoesNotMatchExtension() throws Exception {
        Long customerId = createCustomer();
        Long contractId = createContract(customerId);
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "<script>alert(1)</script>".getBytes());

        mockMvc.perform(multipart("/api/v1/contracts/" + contractId + "/attachments")
                        .file(fakePdf)
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void nonDrafterCannotUpdateContract() throws Exception {
        UserLogin operator = createOperatorUser();
        Long customerId = createCustomer();
        Long contractId = createContract(customerId);

        mockMvc.perform(put("/api/v1/contracts/" + contractId)
                        .header("Authorization", "Bearer " + operator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractPayload(customerId, LocalDate.now(), LocalDate.now().plusDays(2))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void signingRequiresAllSignTasksToFinish() throws Exception {
        UserLogin operator = createOperatorUser();
        Long customerId = createCustomer();
        Long contractId = createContract(customerId);
        Long adminUserId = currentUserId();

        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/assign")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"countersignUserIds":[%d],"approvalUserIds":[%d],"signUserIds":[%d,%d]}
                                """, adminUserId, adminUserId, adminUserId, operator.id())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/countersign")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"opinion\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contract.status").value("COUNTERSIGNED"));

        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/finalize")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"最终合同正文\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contract.status").value("FINALIZED"));

        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/approve")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"APPROVED\",\"opinion\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contract.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/sign")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signInfo\":\"管理员签订\",\"signedDate\":\"2026-05-21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contract.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/sign")
                        .header("Authorization", "Bearer " + operator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signInfo\":\"操作员签订\",\"signedDate\":\"2026-05-21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contract.status").value("SIGNED"));
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"username":"%s","password":"%s"}
                                """, username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    private Long currentUserId() throws Exception {
        String response = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createCustomer() throws Exception {
        String response = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"name":"%s","tel":"13800000000","address":"测试地址"}
                                """, unique("边界客户"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createContract(Long customerId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/contracts")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contractPayload(customerId, LocalDate.now(), LocalDate.now().plusDays(1))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("id").asLong();
    }

    private UserLogin createOperatorUser() throws Exception {
        Long operatorRoleId = findRoleId("ROLE_OPERATOR");
        String username = unique("boundary_operator");
        String response = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"username":"%s","password":"123456","displayName":"边界操作员","roleIds":[%d]}
                                """, username, operatorRoleId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long userId = objectMapper.readTree(response).path("data").path("id").asLong();
        return new UserLogin(userId, login(username, "123456"));
    }

    private Long findRoleId(String roleCode) throws Exception {
        String response = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode role : objectMapper.readTree(response).path("data")) {
            if (roleCode.equals(role.path("roleCode").asText())) {
                return role.path("id").asLong();
            }
        }
        throw new AssertionError("未找到角色: " + roleCode);
    }

    private String contractPayload(Long customerId, LocalDate beginDate, LocalDate endDate) {
        return String.format("""
                {"name":"%s","customerId":%d,"beginDate":"%s","endDate":"%s","content":"合同正文"}
                """, unique("边界合同"), customerId, beginDate, endDate);
    }

    private String bearer() {
        return "Bearer " + adminToken;
    }

    private String unique(String prefix) {
        return prefix + "_" + System.nanoTime();
    }

    private record UserLogin(Long id, String token) {}
}
