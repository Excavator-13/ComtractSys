package com.contractsys.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String adminToken;
    private static String operatorToken;
    private static String contractAdminToken;
    private static String newUserToken;
    private static Long operatorUserId;
    private static Long contractAdminUserId;
    private static Long newUserId;

    // ========== Setup ==========

    @Test
    @Order(1)
    void setup_adminLoginAndCreateTestUsers() throws Exception {
        adminToken = login("admin", "123456");

        // Get roles to find IDs
        String rolesResp = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode rolesArr = objectMapper.readTree(rolesResp).path("data");
        Long operatorRoleId = null;
        Long contractAdminRoleId = null;
        Long newUserRoleId = null;
        for (JsonNode r : rolesArr) {
            switch (r.path("roleCode").asText()) {
                case "ROLE_OPERATOR" -> operatorRoleId = r.path("id").asLong();
                case "ROLE_CONTRACT_ADMIN" -> contractAdminRoleId = r.path("id").asLong();
                case "ROLE_NEW_USER" -> newUserRoleId = r.path("id").asLong();
            }
        }

        // Create operator user
        String opResp = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"username":"test_operator","password":"123456","displayName":"测试操作员","roleIds":[%d]}
                                """, operatorRoleId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        operatorUserId = objectMapper.readTree(opResp).path("data").path("id").asLong();
        operatorToken = login("test_operator", "123456");

        // Create contract admin user
        String caResp = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"username":"test_contract_admin","password":"123456","displayName":"测试合同管理员","roleIds":[%d]}
                                """, contractAdminRoleId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        contractAdminUserId = objectMapper.readTree(caResp).path("data").path("id").asLong();
        contractAdminToken = login("test_contract_admin", "123456");

        // Register new user (no permissions)
        String newUserResp = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test_newuser","password":"123456","confirmPassword":"123456"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        newUserId = objectMapper.readTree(newUserResp).path("data").path("id").asLong();
        newUserToken = login("test_newuser", "123456");
    }

    // ========== Admin tests ==========

    @Test
    @Order(2)
    void adminCanAccessContracts() throws Exception {
        mockMvc.perform(get("/api/v1/contracts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(2)
    void adminCanAccessUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(2)
    void adminCanAccessRoles() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(2)
    void adminCanAccessCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(2)
    void adminCannotBeDeleted() throws Exception {
        Long adminUserId = findUserIdByUsername("admin");

        mockMvc.perform(delete("/api/v1/users/" + adminUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    @Order(2)
    void adminCannotBeDisabled() throws Exception {
        Long adminUserId = findUserIdByUsername("admin");

        mockMvc.perform(patch("/api/v1/users/" + adminUserId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    @Order(2)
    void userViewsExposeBuiltInAdminAndProfileCanBeUpdated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.builtInAdmin").value(true));

        mockMvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"操作员个人设置","phone":"13800000001","email":"operator@example.test"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("操作员个人设置"))
                .andExpect(jsonPath("$.data.phone").value("13800000001"))
                .andExpect(jsonPath("$.data.email").value("operator@example.test"));
    }

    @Test
    @Order(2)
    void permissionsExposeCoreFlagAndCorePermissionsCannotBeDeleted() throws Exception {
        String resp = mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.permissionCode == 'contract:create')].core").value(true))
                .andReturn().getResponse().getContentAsString();

        Long createPermissionId = null;
        for (JsonNode permission : objectMapper.readTree(resp).path("data")) {
            if ("contract:create".equals(permission.path("permissionCode").asText())) {
                createPermissionId = permission.path("id").asLong();
                break;
            }
        }
        if (createPermissionId == null) {
            throw new AssertionError("未找到 contract:create 权限");
        }

        mockMvc.perform(delete("/api/v1/permissions/" + createPermissionId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    @Order(2)
    void contractAssignerCanLoadRoleOptionsForTemplateVisibility() throws Exception {
        mockMvc.perform(get("/api/v1/roles/options")
                        .header("Authorization", "Bearer " + contractAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.roleCode == 'ROLE_OPERATOR')].roleName").exists());
    }

    // ========== Operator tests ==========

    @Test
    @Order(3)
    void operatorCanAccessContracts() throws Exception {
        mockMvc.perform(get("/api/v1/contracts")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(3)
    void operatorCanAccessCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(3)
    void operatorCannotAccessUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void operatorCannotAccessRoles() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(3)
    void operatorCannotAccessAssignableUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users/assignable")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    // ========== Contract admin tests ==========

    @Test
    @Order(4)
    void contractAdminCanAccessContracts() throws Exception {
        mockMvc.perform(get("/api/v1/contracts")
                        .header("Authorization", "Bearer " + contractAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @Order(4)
    void contractAdminCannotAccessCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + contractAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void contractAdminCannotAccessUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + contractAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void contractAdminCanAccessAssignableUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users/assignable")
                        .header("Authorization", "Bearer " + contractAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ========== New user tests ==========

    @Test
    @Order(5)
    void newUserCannotAccessContracts() throws Exception {
        mockMvc.perform(get("/api/v1/contracts")
                        .header("Authorization", "Bearer " + newUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @Order(5)
    void newUserCannotAccessCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + newUserToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void newUserCannotAccessUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + newUserToken))
                .andExpect(status().isForbidden());
    }

    // ========== Built-in role protection ==========

    @Test
    @Order(6)
    void builtInRoleCannotBeDeleted() throws Exception {
        // Get admin role ID
        String resp = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long adminRoleId = null;
        for (JsonNode r : objectMapper.readTree(resp).path("data")) {
            if ("ROLE_ADMIN".equals(r.path("roleCode").asText())) {
                adminRoleId = r.path("id").asLong();
                break;
            }
        }

        mockMvc.perform(delete("/api/v1/roles/" + adminRoleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    @Order(6)
    void builtInRoleNameCannotBeChanged() throws Exception {
        String resp = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long operatorRoleId = null;
        for (JsonNode r : objectMapper.readTree(resp).path("data")) {
            if ("ROLE_OPERATOR".equals(r.path("roleCode").asText())) {
                operatorRoleId = r.path("id").asLong();
                break;
            }
        }

        mockMvc.perform(put("/api/v1/roles/" + operatorRoleId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"被修改的名称\",\"description\":\"test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    @Order(6)
    void builtInRoleCanUpdateDescription() throws Exception {
        String resp = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long operatorRoleId = null;
        String operatorRoleName = null;
        for (JsonNode r : objectMapper.readTree(resp).path("data")) {
            if ("ROLE_OPERATOR".equals(r.path("roleCode").asText())) {
                operatorRoleId = r.path("id").asLong();
                operatorRoleName = r.path("roleName").asText();
                break;
            }
        }

        mockMvc.perform(put("/api/v1/roles/" + operatorRoleId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"roleName\":\"%s\",\"description\":\"更新后的描述\"}", operatorRoleName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    // ========== Unauthenticated access ==========

    @Test
    @Order(7)
    void unauthenticatedUserCannotAccessContracts() throws Exception {
        mockMvc.perform(get("/api/v1/contracts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @Order(7)
    void invalidTokenCannotAccessContracts() throws Exception {
        mockMvc.perform(get("/api/v1/contracts")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @Order(8)
    void disabledUserTokenCannotAccessAuthenticatedEndpoint() throws Exception {
        mockMvc.perform(patch("/api/v1/users/" + operatorUserId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"test_operator","password":"123456"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号已禁用，请联系管理员"));
    }

    @Test
    @Order(8)
    void deletedUserTokenCannotAccessAuthenticatedEndpoint() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + newUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + newUserToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ========== Helper ==========

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"username":"%s","password":"%s"}
                                """, username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("token").asText();
    }

    private Long findUserIdByUsername(String username) throws Exception {
        String resp = mockMvc.perform(get("/api/v1/users?keyword=" + username)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (JsonNode user : objectMapper.readTree(resp).path("data").path("records")) {
            if (username.equals(user.path("username").asText())) {
                return user.path("id").asLong();
            }
        }
        throw new AssertionError("未找到用户: " + username);
    }
}
