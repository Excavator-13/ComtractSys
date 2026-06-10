package com.contractsys;

import com.contractsys.contract.Contract;
import com.contractsys.contract.ContractRepository;
import com.contractsys.customer.Customer;
import com.contractsys.customer.CustomerRepository;
import com.contractsys.user.SysUser;
import com.contractsys.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统计缓存失效链 / 分页与导出截断边界 / CSV 注入防护 / 操作日志审计 / 引用完整性：
 * - 新建、删除、状态流转后统计缓存必须失效（回归 ContractChangedEvent 链路）
 * - 受限用户统计只统计相关合同（回归 countRelatedByStatus）
 * - 分页 API 钳制 200 但 total 正确；导出不受 200 钳制（回归 PageRequests 截断）
 * - CSV 公式注入转义端到端
 * - 操作日志的写入、过滤、导出；失败操作不留日志
 * - 客户-合同、角色-用户、权限-角色 的引用删除保护
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatisticsAuditExportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private UserRepository userRepository;

    private String adminToken;
    private String assignerToken;
    private String drafterAToken;
    private String drafterBToken;
    private String csToken;
    private String apToken;
    private String signerToken;

    private Long assignerId;
    private Long drafterAId;
    private Long csId;
    private Long apId;
    private Long signerId;

    private Long customerId;
    private Long operatorRoleId;
    private Long contractAdminRoleId;
    private Long newUserRoleId;

    private final String suffix = String.valueOf(System.nanoTime() % 1_000_000);

    @BeforeAll
    void setUp() throws Exception {
        adminToken = login("admin", "123456");

        JsonNode roles = getJson("/api/v1/roles", adminToken).get("data");
        for (JsonNode role : roles) {
            switch (role.get("roleCode").asText()) {
                case "ROLE_OPERATOR" -> operatorRoleId = role.get("id").asLong();
                case "ROLE_CONTRACT_ADMIN" -> contractAdminRoleId = role.get("id").asLong();
                case "ROLE_NEW_USER" -> newUserRoleId = role.get("id").asLong();
            }
        }

        assignerId = createUser("st_assigner_" + suffix, contractAdminRoleId);
        drafterAId = createUser("st_drafter_a_" + suffix, operatorRoleId);
        createUser("st_drafter_b_" + suffix, operatorRoleId);
        csId = createUser("st_cs_" + suffix, operatorRoleId);
        apId = createUser("st_ap_" + suffix, operatorRoleId);
        signerId = createUser("st_signer_" + suffix, operatorRoleId);

        assignerToken = login("st_assigner_" + suffix, "test-pass-123");
        drafterAToken = login("st_drafter_a_" + suffix, "test-pass-123");
        drafterBToken = login("st_drafter_b_" + suffix, "test-pass-123");
        csToken = login("st_cs_" + suffix, "test-pass-123");
        apToken = login("st_ap_" + suffix, "test-pass-123");
        signerToken = login("st_signer_" + suffix, "test-pass-123");

        customerId = createCustomer("统计客户" + suffix);
    }

    // ---------- 统计缓存失效链 ----------

    @Test
    void statisticsCacheInvalidatedOnCreateAndDelete() throws Exception {
        JsonNode before = getJson("/api/v1/statistics", adminToken).get("data");
        long total0 = before.get("total").asLong();
        long draft0 = before.get("draft").asLong();
        long monthly0 = currentMonthCount();

        long id = createContract(drafterAToken, "缓存失效合同" + suffix);

        // 新建后立即可见（旧版只在状态流转时失效缓存，create 不刷新 —— 此处是回归断言）
        JsonNode afterCreate = getJson("/api/v1/statistics", adminToken).get("data");
        assertThat(afterCreate.get("total").asLong()).isEqualTo(total0 + 1);
        assertThat(afterCreate.get("draft").asLong()).isEqualTo(draft0 + 1);
        assertThat(currentMonthCount()).isEqualTo(monthly0 + 1);

        // 删除草稿后统计同步回落
        mockMvc.perform(delete("/api/v1/contracts/" + id)
                        .header("Authorization", "Bearer " + assignerToken))
                .andExpect(status().isOk());
        JsonNode afterDelete = getJson("/api/v1/statistics", adminToken).get("data");
        assertThat(afterDelete.get("total").asLong()).isEqualTo(total0);
        assertThat(afterDelete.get("draft").asLong()).isEqualTo(draft0);
        assertThat(currentMonthCount()).isEqualTo(monthly0);
    }

    @Test
    void statisticsReflectWorkflowTransitionsAndTaskCounters() throws Exception {
        JsonNode before = getJson("/api/v1/statistics", adminToken).get("data");
        long signed0 = before.get("signed").asLong();

        // 会签人的个人任务计数器
        JsonNode csStatsBefore = getJson("/api/v1/statistics/tasks/my", csToken).get("data");
        long csPending0 = csStatsBefore.get("pendingTasks").asLong();
        long csDone0 = csStatsBefore.get("doneTasks").asLong();

        long id = createContract(drafterAToken, "流转统计合同" + suffix);
        assign(id, List.of(csId), List.of(apId), signerId);

        // 分配后会签人 pending +1
        assertThat(getJson("/api/v1/statistics/tasks/my", csToken).get("data").get("pendingTasks").asLong())
                .isEqualTo(csPending0 + 1);

        op(csToken, id, "countersign", "{\"opinion\":\"ok\"}", 200);
        // 完成后 pending 回落、done +1
        JsonNode csStatsAfter = getJson("/api/v1/statistics/tasks/my", csToken).get("data");
        assertThat(csStatsAfter.get("pendingTasks").asLong()).isEqualTo(csPending0);
        assertThat(csStatsAfter.get("doneTasks").asLong()).isEqualTo(csDone0 + 1);

        op(drafterAToken, id, "finalize", "{\"content\":\"定稿\"}", 200);
        op(apToken, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"ok\"}", 200);
        op(signerToken, id, "sign",
                "{\"signInfo\":\"sign\",\"signedDate\":\"" + LocalDate.now() + "\"}", 200);

        // 签订完成 -> 全局 signed +1（缓存被状态流转事件失效）
        assertThat(getJson("/api/v1/statistics", adminToken).get("data").get("signed").asLong())
                .isEqualTo(signed0 + 1);
    }

    @Test
    void restrictedUserStatisticsOnlyCountRelatedContracts() throws Exception {
        // 全新的两个操作员，相关合同数从 0 开始，可用绝对值断言
        String ua = "st_scope_a_" + suffix;
        String ub = "st_scope_b_" + suffix;
        createUser(ua, operatorRoleId);
        createUser(ub, operatorRoleId);
        String tokenA = login(ua, "test-pass-123");
        String tokenB = login(ub, "test-pass-123");

        createContract(tokenA, "范围A1_" + suffix);
        createContract(tokenA, "范围A2_" + suffix);
        createContract(tokenB, "范围B1_" + suffix);

        // 操作员无 contract:query -> 只统计自己相关的合同
        JsonNode statsA = getJson("/api/v1/statistics", tokenA).get("data");
        assertThat(statsA.get("total").asLong()).isEqualTo(2);
        assertThat(statsA.get("draft").asLong()).isEqualTo(2);

        JsonNode statsB = getJson("/api/v1/statistics", tokenB).get("data");
        assertThat(statsB.get("total").asLong()).isEqualTo(1);

        // 有 contract:query 的用户看到全局（必然 >= 3）
        assertThat(getJson("/api/v1/statistics", adminToken).get("data").get("total").asLong())
                .isGreaterThanOrEqualTo(3);
    }

    // ---------- 分页钳制与导出截断边界（>200 行） ----------

    @Test
    void paginationClampsAt200ButExportReturnsAllRows() throws Exception {
        String marker = "BULK" + suffix;
        bulkInsertContracts(marker, 205);

        // 分页查询：size 被钳制到 200，但 total 必须如实
        MvcResult page = mockMvc.perform(get("/api/v1/contracts/query")
                        .param("keyword", marker)
                        .param("size", "10000")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode pageData = objectMapper.readTree(page.getResponse().getContentAsString()).get("data");
        assertThat(pageData.get("total").asLong()).isEqualTo(205);
        assertThat(pageData.get("records").size()).isEqualTo(200);

        // 导出：不受 200 钳制，205 行全部落盘（回归 PageRequests 截断 bug）
        MvcResult export = mockMvc.perform(get("/api/v1/contracts/export")
                        .param("keyword", marker)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String csv = new String(export.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        String[] lines = csv.split("\n");
        assertThat(lines.length).isEqualTo(1 + 205); // 表头 + 205 行数据
    }

    // ---------- CSV 注入防护端到端 ----------

    @Test
    void csvExportEscapesFormulaInjectionAndSpecialChars() throws Exception {
        String name = "=HYPERLINK(\"x\"),CSV" + suffix;
        createContract(drafterAToken, name);

        MvcResult export = mockMvc.perform(get("/api/v1/contracts/export")
                        .param("keyword", "CSV" + suffix)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String csv = new String(export.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        // 公式前缀被单引号中和，且因含逗号/引号被整体包裹转义
        assertThat(csv).contains("\"'=HYPERLINK(\"\"x\"\"),CSV" + suffix + "\"");
        // 原始裸公式不应出现在任何行首字段
        for (String line : csv.split("\n")) {
            assertThat(line).doesNotStartWith("=");
        }
    }

    // ---------- 操作日志审计 ----------

    @Test
    void operationLogsCaptureMutationsAndSupportFiltering() throws Exception {
        String customerName = "审计客户" + suffix;
        long cid = createCustomer(customerName);

        mockMvc.perform(put("/api/v1/customers/" + cid)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","tel":"13900000000","address":"修改后的地址"}
                                """.formatted(customerName)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/customers/" + cid)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 新增/修改/删除 各留一条审计日志（按 targetId 精确匹配，删除日志的 content 不含客户名）
        JsonNode logs = getJson("/api/v1/logs?module=CUSTOMER&size=100", adminToken)
                .get("data").get("records");
        List<String> actions = new ArrayList<>();
        for (JsonNode log : logs) {
            if (log.get("targetId").asLong() == cid) {
                actions.add(log.get("action").asText());
            }
        }
        assertThat(actions).contains("新增客户", "修改客户", "删除客户");

        // module 过滤是精确匹配
        JsonNode wrongModule = getJson("/api/v1/logs?module=NOPE&keyword=" + customerName, adminToken)
                .get("data").get("records");
        assertThat(wrongModule.size()).isZero();

        // 日志导出携带 BOM 与表头
        MvcResult export = mockMvc.perform(get("/api/v1/logs/export")
                        .param("module", "CUSTOMER")
                        .param("keyword", customerName)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String csv = new String(export.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF时间,操作人,模块,动作,对象类型,对象ID,内容");
        assertThat(csv).contains(customerName);

        // 操作员（无 log:view）不能看日志
        mockMvc.perform(get("/api/v1/logs").header("Authorization", "Bearer " + drafterAToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedOperationsLeaveNoAuditLog() throws Exception {
        String customerName = "引用客户" + suffix;
        long cid = createCustomer(customerName);
        long contractId = createContractFor(cid, drafterAToken, "引用合同" + suffix);

        // 被合同引用 -> 删除失败 409
        mockMvc.perform(delete("/api/v1/customers/" + cid)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // 失败的删除不应留下"删除客户"日志（事务一致性）
        JsonNode logs = getJson("/api/v1/logs?module=CUSTOMER&size=100", adminToken)
                .get("data").get("records");
        for (JsonNode log : logs) {
            if (log.get("targetId").asLong() == cid) {
                assertThat(log.get("action").asText()).isNotEqualTo("删除客户");
            }
        }

        // 删除引用它的草稿合同后即可删除客户
        mockMvc.perform(delete("/api/v1/contracts/" + contractId)
                        .header("Authorization", "Bearer " + assignerToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/customers/" + cid)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 已删除客户不能再用于起草合同
        mockMvc.perform(post("/api/v1/contracts")
                        .header("Authorization", "Bearer " + drafterAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"幽灵客户合同","customerId":%d,
                                 "beginDate":"%s","endDate":"%s","content":"x"}
                                """.formatted(cid, LocalDate.now(), LocalDate.now().plusDays(30))))
                .andExpect(status().isNotFound());
    }

    // ---------- 角色 / 权限引用完整性 ----------

    @Test
    void roleDeletionBlockedWhileBoundToUser() throws Exception {
        long roleId = createRole("ROLE_TMP_" + suffix, "临时角色" + suffix);
        long userId = createUser("st_rolebind_" + suffix, roleId);

        // 角色被用户绑定 -> 不可删除
        mockMvc.perform(delete("/api/v1/roles/" + roleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // 把用户改派到其他角色后即可删除
        mockMvc.perform(put("/api/v1/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + newUserRoleId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/roles/" + roleId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void permissionDeletionBlockedWhileAssignedAndCoreProtected() throws Exception {
        // 创建自定义权限并挂到自定义角色
        MvcResult created = mockMvc.perform(post("/api/v1/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionCode":"tmp:perm%s","permissionName":"临时权限","module":"SYSTEM"}
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andReturn();
        long permId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        long roleId = createRole("ROLE_PERM_" + suffix, "权限宿主角色" + suffix);

        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[" + permId + "]}"))
                .andExpect(status().isOk());

        // 已分配 -> 不可删除
        mockMvc.perform(delete("/api/v1/permissions/" + permId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // 清空角色权限后即可删除
        mockMvc.perform(put("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[]}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/permissions/" + permId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 核心权限永远不可删除
        JsonNode permissions = getJson("/api/v1/permissions", adminToken).get("data");
        long corePermId = -1;
        for (JsonNode permission : permissions) {
            if ("contract:view".equals(permission.get("permissionCode").asText())) {
                corePermId = permission.get("id").asLong();
            }
        }
        assertThat(corePermId).isPositive();
        mockMvc.perform(delete("/api/v1/permissions/" + corePermId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        // 权限编码创建后不可修改
        mockMvc.perform(put("/api/v1/permissions/" + corePermId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionCode":"contract:view-renamed","permissionName":"查看合同","module":"CONTRACT"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void roleReassignmentTakesEffectOnNextRequest() throws Exception {
        // 新用户默认无任何业务权限
        String username = "st_promote_" + suffix;
        long userId = createUser(username, newUserRoleId);
        String token = login(username, "test-pass-123");

        mockMvc.perform(get("/api/v1/contracts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // 管理员改派操作员角色后，同一个 token 下一次请求立即生效（JWT 过滤器逐请求重载用户）
        mockMvc.perform(put("/api/v1/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + operatorRoleId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/contracts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 降级同样即时生效
        mockMvc.perform(put("/api/v1/users/" + userId + "/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":" + newUserRoleId + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/contracts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ===================== 辅助方法 =====================

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asText();
    }

    private Long createUser(String username, Long roleId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"test-pass-123","displayName":"%s","roleId":%d}
                                """.formatted(username, username, roleId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private long createRole(String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleCode":"%s","roleName":"%s"}
                                """.formatted(code, name)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private long createCustomer(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","tel":"13800000000","address":"测试地址"}
                                """.formatted(name)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private long createContract(String token, String name) throws Exception {
        return createContractFor(customerId, token, name);
    }

    private long createContractFor(long cid, String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contracts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "name", name,
                                "customerId", cid,
                                "beginDate", LocalDate.now().toString(),
                                "endDate", LocalDate.now().plusDays(365).toString(),
                                "content", "测试正文"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private void assign(long contractId, List<Long> csIds, List<Long> apIds, Long signId) throws Exception {
        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/assign")
                        .header("Authorization", "Bearer " + assignerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "countersignUserIds", csIds,
                                "approvalUserIds", apIds,
                                "signUserId", signId))))
                .andExpect(status().isOk());
    }

    private void op(String token, long contractId, String op, String body, int expected) throws Exception {
        var request = post("/api/v1/contracts/" + contractId + "/" + op)
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mockMvc.perform(request).andExpect(status().is(expected));
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long currentMonthCount() throws Exception {
        String month = LocalDate.now().getYear() + "-" + String.format("%02d", LocalDate.now().getMonthValue());
        JsonNode monthly = getJson("/api/v1/statistics/contracts/monthly", adminToken).get("data");
        for (JsonNode entry : monthly) {
            if (month.equals(entry.get("month").asText())) {
                return entry.get("count").asLong();
            }
        }
        return 0;
    }

    /** 直接走仓储批量造数（绕过 API 提速），统计/导出仍走完整 HTTP 链路 */
    private void bulkInsertContracts(String marker, int count) {
        SysUser drafter = userRepository.findByUsernameAndDeletedFalse("st_drafter_a_" + suffix).orElseThrow();
        Customer customer = new Customer();
        customer.setCustomerNo("BLKC" + suffix);
        customer.setName("批量客户" + suffix);
        customer.setTel("13700000000");
        customer.setAddress("批量地址");
        customer = customerRepository.save(customer);

        List<Contract> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Contract contract = new Contract();
            contract.setContractNo(marker + "-" + i);
            contract.setName(marker + "号" + i);
            contract.setCustomer(customer);
            contract.setBeginDate(LocalDate.now());
            contract.setEndDate(LocalDate.now().plusDays(30));
            contract.setContent("批量正文" + i);
            contract.setDrafter(drafter);
            batch.add(contract);
        }
        contractRepository.saveAll(batch);
    }
}
