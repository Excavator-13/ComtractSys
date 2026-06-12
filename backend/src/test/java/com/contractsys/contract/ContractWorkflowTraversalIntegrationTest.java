package com.contractsys.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 合同工作流状态机全遍历测试：
 * - 完整生命周期（多会签/多审批并行汇聚）
 * - 拒绝 -> 修改 -> 重新提交回环
 * - 每个状态下的全部非法转换矩阵
 * - 任务归属与重复完成边界
 * - 分配人员校验边界
 * - 取消/删除对任务和后续操作的封锁
 * - 附件全流程与访问守卫
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContractWorkflowTraversalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;
    private String assignerToken;
    private String drafterToken;
    private String cs1Token;
    private String cs2Token;
    private String ap1Token;
    private String ap2Token;
    private String signerToken;

    private Long assignerId;
    private Long drafterId;
    private Long cs1Id;
    private Long cs2Id;
    private Long ap1Id;
    private Long ap2Id;
    private Long signerId;
    private Long newUserId;

    private Long customerId;
    private Long operatorRoleId;
    private Long contractAdminRoleId;
    private Long newUserRoleId;

    private final String suffix = String.valueOf(System.nanoTime() % 1_000_000);

    @BeforeAll
    void setUpUsersAndCustomer() throws Exception {
        adminToken = login("admin", "123456");

        JsonNode roles = getJson("/api/v1/roles", adminToken);
        for (JsonNode role : roles.get("data")) {
            switch (role.get("roleCode").asText()) {
                case "ROLE_OPERATOR" -> operatorRoleId = role.get("id").asLong();
                case "ROLE_CONTRACT_ADMIN" -> contractAdminRoleId = role.get("id").asLong();
                case "ROLE_NEW_USER" -> newUserRoleId = role.get("id").asLong();
            }
        }
        assertThat(operatorRoleId).isNotNull();
        assertThat(contractAdminRoleId).isNotNull();
        assertThat(newUserRoleId).isNotNull();

        // 分配人必须在合同创建之前存在（创建合同时为所有 contract:assign 用户生成待分配任务）
        assignerId = createUser("wf_assigner_" + suffix, contractAdminRoleId);
        drafterId = createUser("wf_drafter_" + suffix, operatorRoleId);
        cs1Id = createUser("wf_cs1_" + suffix, operatorRoleId);
        cs2Id = createUser("wf_cs2_" + suffix, operatorRoleId);
        ap1Id = createUser("wf_ap1_" + suffix, operatorRoleId);
        ap2Id = createUser("wf_ap2_" + suffix, operatorRoleId);
        signerId = createUser("wf_signer_" + suffix, operatorRoleId);
        newUserId = createUser("wf_newbie_" + suffix, newUserRoleId);

        assignerToken = login("wf_assigner_" + suffix, "test-pass-123");
        drafterToken = login("wf_drafter_" + suffix, "test-pass-123");
        cs1Token = login("wf_cs1_" + suffix, "test-pass-123");
        cs2Token = login("wf_cs2_" + suffix, "test-pass-123");
        ap1Token = login("wf_ap1_" + suffix, "test-pass-123");
        ap2Token = login("wf_ap2_" + suffix, "test-pass-123");
        signerToken = login("wf_signer_" + suffix, "test-pass-123");

        MvcResult customer = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"流程客户%s","tel":"13800000000","address":"测试地址1号"}
                                """.formatted(suffix)))
                .andExpect(status().isOk())
                .andReturn();
        customerId = objectMapper.readTree(customer.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    // ---------- 场景 1：完整生命周期，多会签/多审批并行汇聚 ----------

    @Test
    void fullLifecycleWithParallelCountersignAndApproval() throws Exception {
        long id = createContract("全流程合同" + suffix);
        assertStatus(id, "DRAFT");

        // 分配人能在待办里看到 ASSIGN 任务
        assertThat(myTaskTypesForContract(assignerToken, id)).contains("ASSIGN");

        assign(id, List.of(cs1Id, cs2Id), List.of(ap1Id, ap2Id), signerId);
        assertStatus(id, "ASSIGNED");
        assertThat(myTaskTypesForContract(assignerToken, id)).isEmpty();

        // 第一个会签完成后仍停留在 ASSIGNED
        opOk(cs1Token, id, "countersign", "{\"opinion\":\"同意条款\"}");
        assertStatus(id, "ASSIGNED");

        // 全部会签完成 -> COUNTERSIGNED，起草人收到定稿任务
        opOk(cs2Token, id, "countersign", "{\"opinion\":\"条款无异议\"}");
        assertStatus(id, "COUNTERSIGNED");
        assertThat(myTaskTypesForContract(drafterToken, id)).contains("FINALIZE");

        // 起草人定稿产生新版本
        opOk(drafterToken, id, "finalize", "{\"content\":\"定稿后的合同正文 v2\"}");
        assertStatus(id, "FINALIZED");
        JsonNode versions = getJson("/api/v1/contracts/" + id + "/versions", adminToken);
        assertThat(versions.get("data").size()).isEqualTo(2);
        assertThat(versions.get("data").get(0).get("versionNo").asInt()).isEqualTo(2);

        // 第一个审批通过后仍停留在 FINALIZED
        opOk(ap1Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"同意\"}");
        assertStatus(id, "FINALIZED");

        // 全部审批通过 -> APPROVED，签订人收到任务
        opOk(ap2Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"同意\"}");
        assertStatus(id, "APPROVED");
        assertThat(myTaskTypesForContract(signerToken, id)).contains("SIGN");

        // 签订 -> SIGNED，签订日期回显
        String today = LocalDate.now().toString();
        mockMvc.perform(post("/api/v1/contracts/" + id + "/sign")
                        .header("Authorization", "Bearer " + signerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signInfo\":\"双方现场签订\",\"signedDate\":\"" + today + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contract.status").value("SIGNED"))
                .andExpect(jsonPath("$.data.contract.signedDate").value(today));

        // 时间线完整且转换链正确
        JsonNode timeline = getJson("/api/v1/contracts/" + id + "/timeline", adminToken).get("data");
        List<String> transitions = new java.util.ArrayList<>();
        for (JsonNode entry : timeline) {
            String from = entry.get("fromStatus").isNull() ? "-" : entry.get("fromStatus").asText();
            transitions.add(from + ">" + entry.get("toStatus").asText());
        }
        assertThat(transitions).containsExactly(
                "->DRAFT",
                "DRAFT>ASSIGNED",
                "ASSIGNED>COUNTERSIGNED",
                "COUNTERSIGNED>FINALIZED",
                "FINALIZED>APPROVED",
                "APPROVED>SIGNED");

        // 所有参与者对该合同的待办均已清空
        for (String token : List.of(cs1Token, cs2Token, ap1Token, ap2Token, signerToken, drafterToken)) {
            assertThat(myTaskTypesForContract(token, id)).isEmpty();
        }

        // process 端点聚合视图可用
        mockMvc.perform(get("/api/v1/contracts/" + id + "/process")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contract.status").value("SIGNED"))
                .andExpect(jsonPath("$.data.tasks").isArray())
                .andExpect(jsonPath("$.data.histories").isArray());
    }

    // ---------- 场景 2：拒绝 -> 修改 -> 重新提交回环 ----------

    @Test
    void rejectionAndResubmitLoopResetsApprovalTasks() throws Exception {
        long id = driveToFinalized(List.of(cs1Id), List.of(ap1Id, ap2Id), signerId, "拒绝回环合同" + suffix);

        // 任一审批拒绝立即进入 REJECTED
        opOk(ap1Token, id, "approve", "{\"result\":\"REJECTED\",\"opinion\":\"条款风险高\"}");
        assertStatus(id, "REJECTED");

        // 已拒绝后，另一审批人即使有 PENDING 任务也不能再审批（状态闸门）
        opExpect(ap2Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"同意\"}", 409);

        // 起草人在 REJECTED 状态可以修改合同（产生新版本）
        mockMvc.perform(put("/api/v1/contracts/" + id)
                        .header("Authorization", "Bearer " + drafterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"拒绝回环合同%s","customerId":%d,
                                 "beginDate":"%s","endDate":"%s","content":"按审批意见修改后的正文"}
                                """.formatted(suffix, customerId, LocalDate.now(), LocalDate.now().plusDays(365))))
                .andExpect(status().isOk());

        // 非起草人不能重新提交（即使拥有 contract:update 权限）
        opExpect(cs1Token, id, "resubmit", null, 403);

        // 起草人重新提交 -> FINALIZED，全部审批任务重置为 PENDING
        opOk(drafterToken, id, "resubmit", null);
        assertStatus(id, "FINALIZED");
        assertThat(myTaskTypesForContract(ap1Token, id)).contains("APPROVAL");
        assertThat(myTaskTypesForContract(ap2Token, id)).contains("APPROVAL");

        // 第二轮审批全部通过
        opOk(ap1Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"修改后同意\"}");
        opOk(ap2Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"同意\"}");
        assertStatus(id, "APPROVED");
        assertThat(myTaskTypesForContract(signerToken, id)).contains("SIGN");

        opOk(signerToken, id, "sign", "{\"signInfo\":\"重提后签订\",\"signedDate\":\"" + LocalDate.now() + "\"}");
        assertStatus(id, "SIGNED");
    }

    @Test
    void resubmitThenReturnToDraftPreservesCountersignAssignees() throws Exception {
        long id = driveToFinalized(List.of(cs1Id, cs2Id), List.of(ap1Id, ap2Id), signerId,
                "重提后打回起草合同" + suffix);

        opOk(ap1Token, id, "approve", "{\"result\":\"REJECTED\",\"opinion\":\"审批拒绝后需修改\"}");
        assertStatus(id, "REJECTED");

        opOk(drafterToken, id, "resubmit", null);
        assertStatus(id, "FINALIZED");
        assertThat(myTaskTypesForContract(ap1Token, id)).contains("APPROVAL");

        opOk(ap1Token, id, "return", "{\"targetStage\":\"DRAFT\",\"opinion\":\"退回重新起草\"}");
        assertStatus(id, "RETURNED");

        opOk(drafterToken, id, "resume", null);
        assertStatus(id, "ASSIGNED");
        assertThat(myTaskTypesForContract(cs1Token, id)).contains("COUNTERSIGN");
        assertThat(myTaskTypesForContract(cs2Token, id)).contains("COUNTERSIGN");

        opOk(cs1Token, id, "countersign", "{\"opinion\":\"第三轮会签同意\"}");
        assertStatus(id, "ASSIGNED");
        opOk(cs2Token, id, "countersign", "{\"opinion\":\"第三轮会签同意\"}");
        assertStatus(id, "COUNTERSIGNED");

        opOk(drafterToken, id, "finalize", "{\"content\":\"第三轮定稿正文\"}");
        assertStatus(id, "FINALIZED");
        assertThat(myTaskTypesForContract(ap1Token, id)).contains("APPROVAL");
        assertThat(myTaskTypesForContract(ap2Token, id)).contains("APPROVAL");

        opOk(ap1Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"第三轮审批通过\"}");
        opOk(ap2Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"第三轮审批通过\"}");
        assertStatus(id, "APPROVED");

        opOk(signerToken, id, "sign", "{\"signInfo\":\"第三轮签订\",\"signedDate\":\"" + LocalDate.now() + "\"}");
        assertStatus(id, "SIGNED");

        JsonNode detail = getJson("/api/v1/contracts/" + id, adminToken).get("data");
        assertThat(detail.get("contract").get("currentRound").asInt()).isEqualTo(3);
        assertThat(taskRounds(detail)).contains(1, 2, 3);
    }

    @Test
    void withdrawFinalizeAndApprovalCanContinueToNextStage() throws Exception {
        long finalizeWithdrawId = driveToFinalized(List.of(cs1Id), List.of(ap1Id), signerId, "撤回定稿合同" + suffix);

        opOk(drafterToken, finalizeWithdrawId, "tasks/withdraw", null);
        assertStatus(finalizeWithdrawId, "COUNTERSIGNED");
        assertThat(myTaskTypesForContract(drafterToken, finalizeWithdrawId)).contains("FINALIZE");

        opOk(drafterToken, finalizeWithdrawId, "finalize", "{\"content\":\"撤回后重新定稿\"}");
        assertStatus(finalizeWithdrawId, "FINALIZED");
        assertThat(myTaskTypesForContract(ap1Token, finalizeWithdrawId)).contains("APPROVAL");

        opOk(ap1Token, finalizeWithdrawId, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"重新定稿后通过\"}");
        assertStatus(finalizeWithdrawId, "APPROVED");
        assertThat(myTaskTypesForContract(signerToken, finalizeWithdrawId)).contains("SIGN");

        long approvalWithdrawId = driveToFinalized(List.of(cs1Id), List.of(ap1Id), signerId, "撤回审批合同" + suffix);
        opOk(ap1Token, approvalWithdrawId, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"先通过\"}");
        assertStatus(approvalWithdrawId, "APPROVED");

        opOk(ap1Token, approvalWithdrawId, "tasks/withdraw", null);
        assertStatus(approvalWithdrawId, "FINALIZED");
        assertThat(myTaskTypesForContract(ap1Token, approvalWithdrawId)).contains("APPROVAL");

        opOk(ap1Token, approvalWithdrawId, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"撤回后重新通过\"}");
        assertStatus(approvalWithdrawId, "APPROVED");
        assertThat(myTaskTypesForContract(signerToken, approvalWithdrawId)).contains("SIGN");

        opOk(signerToken, approvalWithdrawId, "sign", "{\"signInfo\":\"撤回审批后签订\",\"signedDate\":\"" + LocalDate.now() + "\"}");
        assertStatus(approvalWithdrawId, "SIGNED");
    }

    @Test
    void returnToFinalizeCreatesNewRoundWithoutOverwritingHistory() throws Exception {
        long id = driveToFinalized(List.of(cs1Id), List.of(ap1Id, ap2Id), signerId, "多轮打回合同" + suffix);

        opOk(ap1Token, id, "return", "{\"targetStage\":\"FINALIZE\",\"opinion\":\"付款条款需调整\"}");
        assertStatus(id, "RETURNED");

        JsonNode returnedDetail = getJson("/api/v1/contracts/" + id, adminToken).get("data");
        assertThat(returnedDetail.get("contract").get("currentRound").asInt()).isEqualTo(1);
        assertThat(returnedDetail.get("contract").get("returnTargetStage").asText()).isEqualTo("FINALIZE");
        assertThat(taskOpinions(returnedDetail)).anyMatch(opinion -> opinion.contains("付款条款需调整"));
        assertThat(myTaskTypesForContract(ap2Token, id)).isEmpty();

        opOk(drafterToken, id, "resume", null);
        assertStatus(id, "COUNTERSIGNED");
        assertThat(myTaskTypesForContract(drafterToken, id)).contains("FINALIZE");

        opOk(drafterToken, id, "finalize", "{\"content\":\"第二轮定稿正文\"}");
        assertStatus(id, "FINALIZED");
        assertThat(myTaskTypesForContract(ap1Token, id)).contains("APPROVAL");
        assertThat(myTaskTypesForContract(ap2Token, id)).contains("APPROVAL");

        JsonNode secondRoundDetail = getJson("/api/v1/contracts/" + id, adminToken).get("data");
        assertThat(secondRoundDetail.get("contract").get("currentRound").asInt()).isEqualTo(2);
        assertThat(taskRounds(secondRoundDetail)).contains(1, 2);
        assertThat(taskOpinions(secondRoundDetail)).anyMatch(opinion -> opinion.contains("付款条款需调整"));
    }

    // ---------- 场景 3：每个状态下的非法转换矩阵 ----------

    @Test
    void illegalTransitionMatrixAcrossAllStates() throws Exception {
        long id = createContract("非法转换矩阵合同" + suffix);

        // DRAFT：除 assign/update/delete 外全部 409
        opExpect(cs1Token, id, "countersign", "{\"opinion\":\"x\"}", 409);
        opExpect(drafterToken, id, "finalize", "{\"content\":\"x\"}", 409);
        opExpect(ap1Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"x\"}", 409);
        opExpect(signerToken, id, "sign", "{\"signInfo\":\"x\",\"signedDate\":\"" + LocalDate.now() + "\"}", 409);
        opExpect(drafterToken, id, "resubmit", null, 409);

        assign(id, List.of(cs1Id), List.of(ap1Id), signerId);

        // ASSIGNED：重复分配 / 修改 / 删除 / 定稿 / 审批 / 签订均 409
        mockMvc.perform(post("/api/v1/contracts/" + id + "/assign")
                        .header("Authorization", "Bearer " + assignerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(List.of(cs2Id), List.of(ap2Id), signerId)))
                .andExpect(status().isConflict());
        updateExpect(drafterToken, id, 409);
        mockMvc.perform(delete("/api/v1/contracts/" + id)
                        .header("Authorization", "Bearer " + assignerToken))
                .andExpect(status().isConflict());
        opExpect(drafterToken, id, "finalize", "{\"content\":\"x\"}", 409);
        opExpect(ap1Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"x\"}", 409);
        opExpect(signerToken, id, "sign", "{\"signInfo\":\"x\",\"signedDate\":\"" + LocalDate.now() + "\"}", 409);

        opOk(cs1Token, id, "countersign", "{\"opinion\":\"ok\"}");
        assertStatus(id, "COUNTERSIGNED");

        // COUNTERSIGNED：会签 / 审批 / 签订均 409（修改对起草人合法，不在此列）
        opExpect(cs1Token, id, "countersign", "{\"opinion\":\"x\"}", 409);
        opExpect(ap1Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"x\"}", 409);
        opExpect(signerToken, id, "sign", "{\"signInfo\":\"x\",\"signedDate\":\"" + LocalDate.now() + "\"}", 409);

        opOk(drafterToken, id, "finalize", "{\"content\":\"定稿\"}");
        assertStatus(id, "FINALIZED");

        // FINALIZED：修改 / 再定稿 / 会签 / 签订均 409
        updateExpect(drafterToken, id, 409);
        opExpect(drafterToken, id, "finalize", "{\"content\":\"x\"}", 409);
        opExpect(cs1Token, id, "countersign", "{\"opinion\":\"x\"}", 409);
        opExpect(signerToken, id, "sign", "{\"signInfo\":\"x\",\"signedDate\":\"" + LocalDate.now() + "\"}", 409);

        opOk(ap1Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"ok\"}");
        assertStatus(id, "APPROVED");

        // APPROVED：再审批 / 会签 / 修改均 409
        opExpect(ap1Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"x\"}", 409);
        opExpect(cs1Token, id, "countersign", "{\"opinion\":\"x\"}", 409);
        updateExpect(drafterToken, id, 409);

        opOk(signerToken, id, "sign", "{\"signInfo\":\"sign\",\"signedDate\":\"" + LocalDate.now() + "\"}");
        assertStatus(id, "SIGNED");

        // SIGNED：取消 / 再签订 / 修改 / 删除均 409
        mockMvc.perform(post("/api/v1/contracts/" + id + "/cancel")
                        .header("Authorization", "Bearer " + assignerToken))
                .andExpect(status().isConflict());
        opExpect(signerToken, id, "sign", "{\"signInfo\":\"x\",\"signedDate\":\"" + LocalDate.now() + "\"}", 409);
        updateExpect(drafterToken, id, 409);
        mockMvc.perform(delete("/api/v1/contracts/" + id)
                        .header("Authorization", "Bearer " + assignerToken))
                .andExpect(status().isConflict());
    }

    // ---------- 场景 4：任务归属与重复完成 ----------

    @Test
    void taskOwnershipAndDoubleCompletionBoundaries() throws Exception {
        long id = createContract("任务归属合同" + suffix);
        assign(id, List.of(cs1Id, cs2Id), List.of(ap1Id), signerId);

        // 无任务的同权限用户操作 -> 403
        opExpect(signerToken, id, "countersign", "{\"opinion\":\"蹭一下\"}", 403);

        // 正常完成一次
        opOk(cs1Token, id, "countersign", "{\"opinion\":\"ok\"}");

        // 同一用户重复完成 -> 403（任务已不在 PENDING，状态仍是 ASSIGNED）
        opExpect(cs1Token, id, "countersign", "{\"opinion\":\"再来一次\"}", 403);
        assertStatus(id, "ASSIGNED");

        opOk(cs2Token, id, "countersign", "{\"opinion\":\"ok\"}");
        assertStatus(id, "COUNTERSIGNED");

        // 非起草人定稿 -> 403
        opExpect(cs1Token, id, "finalize", "{\"content\":\"我来定稿\"}", 403);

        opOk(drafterToken, id, "finalize", "{\"content\":\"定稿\"}");

        // 无审批任务的用户审批 -> 403
        opExpect(cs1Token, id, "approve", "{\"result\":\"APPROVED\",\"opinion\":\"蹭审批\"}", 403);
    }

    // ---------- 场景 5：分配校验边界 ----------

    @Test
    void assignmentValidationEdgeCases() throws Exception {
        long id = createContract("分配校验合同" + suffix);

        // 会签人重复
        assignExpect(id, List.of(cs1Id, cs1Id), List.of(ap1Id), signerId, 409);
        // 会签人包含起草人
        assignExpect(id, List.of(drafterId), List.of(ap1Id), signerId, 409);
        // 审批人包含起草人
        assignExpect(id, List.of(cs1Id), List.of(drafterId), signerId, 409);
        // 签订人是起草人
        assignExpect(id, List.of(cs1Id), List.of(ap1Id), drafterId, 409);
        // 不存在的用户
        assignExpect(id, List.of(999999L), List.of(ap1Id), signerId, 404);
        // 无流程权限的用户（新注册用户无 contract:countersign）
        assignExpect(id, List.of(newUserId), List.of(ap1Id), signerId, 409);

        // 被禁用的用户不能被分配
        long disabledId = createUser("wf_disabled_" + suffix, operatorRoleId);
        mockMvc.perform(patch("/api/v1/users/" + disabledId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        assignExpect(id, List.of(disabledId), List.of(ap1Id), signerId, 409);

        // 校验失败后合同仍停留在 DRAFT，未产生脏任务
        assertStatus(id, "DRAFT");
        assign(id, List.of(cs1Id), List.of(ap1Id), signerId);
        assertStatus(id, "ASSIGNED");
    }

    // ---------- 场景 6：取消封锁一切，删除仅限草稿/已取消 ----------

    @Test
    void cancelClosesTasksAndLocksContract() throws Exception {
        long id = createContract("取消封锁合同" + suffix);
        assign(id, List.of(cs1Id), List.of(ap1Id), signerId);
        assertThat(myTaskTypesForContract(cs1Token, id)).contains("COUNTERSIGN");

        mockMvc.perform(post("/api/v1/contracts/" + id + "/cancel")
                        .header("Authorization", "Bearer " + assignerToken))
                .andExpect(status().isOk());
        assertStatus(id, "CANCELLED");

        // 待办全部关闭
        assertThat(myTaskTypesForContract(cs1Token, id)).isEmpty();
        assertThat(myTaskTypesForContract(signerToken, id)).isEmpty();

        // 任何流程操作均被"已取消"闸门拦截
        opExpect(cs1Token, id, "countersign", "{\"opinion\":\"x\"}", 409);
        updateExpect(drafterToken, id, 409);
        opExpect(drafterToken, id, "resubmit", null, 409);

        // 已取消的合同可以删除，删除后对外不可见
        mockMvc.perform(delete("/api/v1/contracts/" + id)
                        .header("Authorization", "Bearer " + assignerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/contracts/" + id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ---------- 场景 7：附件全流程与访问守卫 ----------

    @Test
    void attachmentLifecycleRespectsAccessGuard() throws Exception {
        long id = createContract("附件流程合同" + suffix);

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "agreement.pdf", "application/pdf",
                "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes());

        // 起草人上传
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/contracts/" + id + "/attachments")
                        .file(pdf)
                        .header("Authorization", "Bearer " + drafterToken))
                .andExpect(status().isOk())
                .andReturn();
        long attachmentId = objectMapper.readTree(uploaded.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        // 起草人可列出，全局查询权限可列出
        mockMvc.perform(get("/api/v1/contracts/" + id + "/attachments")
                        .header("Authorization", "Bearer " + drafterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/v1/contracts/" + id + "/attachments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 与合同无关的操作员不可见（访问守卫）
        mockMvc.perform(get("/api/v1/contracts/" + id + "/attachments")
                        .header("Authorization", "Bearer " + signerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/attachments/" + attachmentId + "/download")
                        .header("Authorization", "Bearer " + signerToken))
                .andExpect(status().isForbidden());

        // 起草人可下载
        mockMvc.perform(get("/api/v1/attachments/" + attachmentId + "/download")
                        .header("Authorization", "Bearer " + drafterToken))
                .andExpect(status().isOk());

        // 无关操作员不能删除附件
        mockMvc.perform(delete("/api/v1/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + signerToken))
                .andExpect(status().isForbidden());

        // 起草人删除附件
        mockMvc.perform(delete("/api/v1/attachments/" + attachmentId)
                        .header("Authorization", "Bearer " + drafterToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/contracts/" + id + "/attachments")
                        .header("Authorization", "Bearer " + drafterToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 已取消的合同不能再上传附件
        mockMvc.perform(post("/api/v1/contracts/" + id + "/cancel")
                        .header("Authorization", "Bearer " + assignerToken))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/contracts/" + id + "/attachments")
                        .file(pdf)
                        .header("Authorization", "Bearer " + drafterToken))
                .andExpect(status().isConflict());
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

    private long createContract(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contracts")
                        .header("Authorization", "Bearer " + drafterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","customerId":%d,
                                 "beginDate":"%s","endDate":"%s","content":"初始合同正文"}
                                """.formatted(name, customerId, LocalDate.now(), LocalDate.now().plusDays(365))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private String assignBody(List<Long> csIds, List<Long> apIds, Long signId) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "countersignUserIds", csIds,
                "approvalUserIds", apIds,
                "signUserId", signId));
    }

    private void assign(long contractId, List<Long> csIds, List<Long> apIds, Long signId) throws Exception {
        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/assign")
                        .header("Authorization", "Bearer " + assignerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(csIds, apIds, signId)))
                .andExpect(status().isOk());
    }

    private void assignExpect(long contractId, List<Long> csIds, List<Long> apIds, Long signId, int expected) throws Exception {
        mockMvc.perform(post("/api/v1/contracts/" + contractId + "/assign")
                        .header("Authorization", "Bearer " + assignerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody(csIds, apIds, signId)))
                .andExpect(status().is(expected));
    }

    private void opOk(String token, long contractId, String op, String body) throws Exception {
        opExpect(token, contractId, op, body, 200);
    }

    private void opExpect(String token, long contractId, String op, String body, int expected) throws Exception {
        var request = post("/api/v1/contracts/" + contractId + "/" + op)
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mockMvc.perform(request).andExpect(status().is(expected));
    }

    private void updateExpect(String token, long contractId, int expected) throws Exception {
        mockMvc.perform(put("/api/v1/contracts/" + contractId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"改名尝试","customerId":%d,
                                 "beginDate":"%s","endDate":"%s","content":"改动"}
                                """.formatted(customerId, LocalDate.now(), LocalDate.now().plusDays(30))))
                .andExpect(status().is(expected));
    }

    private void assertStatus(long contractId, String expected) throws Exception {
        mockMvc.perform(get("/api/v1/contracts/" + contractId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contract.status").value(expected));
    }

    private List<String> myTaskTypesForContract(String token, long contractId) throws Exception {
        JsonNode tasks = getJson("/api/v1/tasks/my", token).get("data");
        List<String> types = new java.util.ArrayList<>();
        for (JsonNode task : tasks) {
            if (task.get("contractId").asLong() == contractId) {
                types.add(task.get("taskType").asText());
            }
        }
        return types;
    }

    private JsonNode getJson(String url, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<Integer> taskRounds(JsonNode detail) {
        List<Integer> rounds = new java.util.ArrayList<>();
        for (JsonNode task : detail.get("tasks")) {
            int round = task.hasNonNull("round") ? task.get("round").asInt() : 1;
            if (!rounds.contains(round)) {
                rounds.add(round);
            }
        }
        return rounds;
    }

    private List<String> taskOpinions(JsonNode detail) {
        List<String> opinions = new java.util.ArrayList<>();
        for (JsonNode task : detail.get("tasks")) {
            if (task.hasNonNull("opinion")) {
                opinions.add(task.get("opinion").asText());
            }
        }
        return opinions;
    }

    /** 走到 FINALIZED：create -> assign -> 全部会签 -> 定稿 */
    private long driveToFinalized(List<Long> csIds, List<Long> apIds, Long signId, String name) throws Exception {
        long id = createContract(name);
        assign(id, csIds, apIds, signId);
        for (Long csId : csIds) {
            String token = tokenOf(csId);
            opOk(token, id, "countersign", "{\"opinion\":\"同意\"}");
        }
        opOk(drafterToken, id, "finalize", "{\"content\":\"定稿正文\"}");
        return id;
    }

    private String tokenOf(Long userId) {
        if (userId.equals(cs1Id)) return cs1Token;
        if (userId.equals(cs2Id)) return cs2Token;
        if (userId.equals(ap1Id)) return ap1Token;
        if (userId.equals(ap2Id)) return ap2Token;
        if (userId.equals(signerId)) return signerToken;
        throw new IllegalArgumentException("unknown test user id " + userId);
    }
}
