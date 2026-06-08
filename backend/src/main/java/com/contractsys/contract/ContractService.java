package com.contractsys.contract;

import com.contractsys.common.ApiException;
import com.contractsys.common.PageRequests;
import com.contractsys.contract.dto.*;
import com.contractsys.customer.Customer;
import com.contractsys.customer.CustomerRepository;
import com.contractsys.log.OperationLogService;
import com.contractsys.user.SysUser;
import com.contractsys.user.UserRepository;
import com.contractsys.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ContractService {
    private final ContractRepository contractRepository;
    private final ContractTaskRepository taskRepository;
    private final ContractStateHistoryRepository stateHistoryRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final OperationLogService operationLogService;

    public ContractService(ContractRepository contractRepository, ContractTaskRepository taskRepository,
                           ContractStateHistoryRepository stateHistoryRepository, CustomerRepository customerRepository,
                           UserRepository userRepository, OperationLogService operationLogService) {
        this.contractRepository = contractRepository;
        this.taskRepository = taskRepository;
        this.stateHistoryRepository = stateHistoryRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.operationLogService = operationLogService;
    }

    public Page<ContractView> list(String keyword, String statusStr, int page, int size, SysUser user) {
        ContractStatus status = parseStatus(statusStr);
        return contractRepository.searchRelated(
                keyword == null ? "" : keyword,
                status,
                user.getId(),
                hasPermission(user, "contract:assign"),
                ContractStatus.DRAFT,
                PageRequests.of(page, size)
        ).map(ContractView::from);
    }

    public Page<ContractView> query(String keyword, String statusStr, int page, int size) {
        ContractStatus status = parseStatus(statusStr);
        return contractRepository.search(
                keyword == null ? "" : keyword, status,
                PageRequests.of(page, size)
        ).map(ContractView::from);
    }

    public ContractDetailView detail(Long id, SysUser user) {
        Contract contract = getContract(id);
        ensureCanViewContract(contract, user);
        List<TaskView> tasks = taskRepository.findByContractIdOrderByCreatedAtAsc(id).stream().map(TaskView::from).toList();
        return new ContractDetailView(ContractView.from(contract), tasks);
    }

    public Map<String, Object> process(Long id, SysUser user) {
        Contract contract = getContract(id);
        ensureCanViewContract(contract, user);
        return Map.of(
                "contract", ContractView.from(contract),
                "tasks", taskRepository.findByContractIdOrderByCreatedAtAsc(id).stream().map(TaskView::from).toList(),
                "histories", stateHistoryRepository.findByContractIdOrderByCreatedAtAsc(id)
        );
    }

    @Transactional
    public ContractView create(ContractCreateRequest request, SysUser operator) {
        if (request.endDate().isBefore(request.beginDate())) {
            throw ApiException.badRequest("结束日期不能早于开始日期");
        }
        Customer customer = customerRepository.findById(request.customerId())
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> ApiException.notFound("客户不存在"));
        Contract contract = new Contract();
        contract.setContractNo("HT" + LocalDate.now().toString().replace("-", "") + System.currentTimeMillis() % 100000);
        contract.setName(request.name());
        contract.setCustomer(customer);
        contract.setBeginDate(request.beginDate());
        contract.setEndDate(request.endDate());
        contract.setContent(request.content());
        contract.setDrafter(operator);
        Contract saved = contractRepository.save(contract);
        recordState(saved, null, ContractStatus.DRAFT, operator, "起草合同");
        createAssignTasks(saved);
        return ContractView.from(saved);
    }

    @Transactional
    public ContractDetailView assign(Long id, AssignRequest request, SysUser operator) {
        Contract contract = getContract(id);
        ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.DRAFT);
        finishTask(id, operator, TaskType.ASSIGN, TaskStatus.DONE, "已完成分配");
        completeRemainingPendingTasks(id, TaskType.ASSIGN, "其他分配待办已关闭");
        validateAssignees(contract, request.countersignUserIds(), "会签人员");
        validateAssignees(contract, request.approvalUserIds(), "审批人员");
        if (request.signUserId().equals(contract.getDrafter().getId())) {
            throw ApiException.conflict("签订人员不能是起草人");
        }
        createUniqueTasks(contract, request.countersignUserIds(), TaskType.COUNTERSIGN, "contract:countersign");
        createUniqueTasks(contract, request.approvalUserIds(), TaskType.APPROVAL, "contract:approve");
        createTask(contract, request.signUserId(), TaskType.SIGN, "contract:sign");
        changeStatus(contract, ContractStatus.ASSIGNED, operator, "管理员分配合同流程人员");
        return detail(id, operator);
    }

    public List<TaskView> myTasks(SysUser user) {
        return taskRepository.findByAssigneeAndTaskStatus(user, TaskStatus.PENDING).stream().map(TaskView::from).toList();
    }

    @Transactional
    public ContractDetailView countersign(Long id, OpinionRequest request, SysUser operator) {
        Contract contract = getContract(id);
        ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.ASSIGNED);
        finishTask(id, operator, TaskType.COUNTERSIGN, TaskStatus.DONE, request.opinion());
        if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatus(id, TaskType.COUNTERSIGN, TaskStatus.PENDING)) {
            changeStatus(contract, ContractStatus.COUNTERSIGNED, operator, "全部会签完成");
            createTask(contract, contract.getDrafter().getId(), TaskType.FINALIZE, "contract:update");
        }
        return detail(id, operator);
    }

    @Transactional
    public ContractDetailView finalizeContract(Long id, FinalizeRequest request, SysUser operator) {
        Contract contract = getContract(id);
        ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.COUNTERSIGNED, ContractStatus.REJECTED);
        if (!contract.getDrafter().getId().equals(operator.getId())) {
            throw ApiException.forbidden("只有起草人可以定稿");
        }
        finishTask(id, operator, TaskType.FINALIZE, TaskStatus.DONE, "起草人定稿");
        contract.setContent(request.content());
        changeStatus(contract, ContractStatus.FINALIZED, operator, "起草人定稿");
        return detail(id, operator);
    }

    @Transactional
    public ContractDetailView approve(Long id, ApproveRequest request, SysUser operator) {
        Contract contract = getContract(id);
        ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.FINALIZED);
        TaskStatus taskStatus = request.result() == ApproveResult.APPROVED ? TaskStatus.DONE : TaskStatus.REJECTED;
        finishTask(id, operator, TaskType.APPROVAL, taskStatus, request.opinion());
        if (taskStatus == TaskStatus.REJECTED) {
            changeStatus(contract, ContractStatus.REJECTED, operator, "审批拒绝");
        } else if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatus(id, TaskType.APPROVAL, TaskStatus.PENDING)) {
            changeStatus(contract, ContractStatus.APPROVED, operator, "全部审批通过");
        }
        return detail(id, operator);
    }

    @Transactional
    public ContractDetailView sign(Long id, SignRequest request, SysUser operator) {
        Contract contract = getContract(id);
        ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.APPROVED);
        finishTask(id, operator, TaskType.SIGN, TaskStatus.DONE, request.signInfo());
        contract.setSignedDate(request.signedDate());
        contract.setSignInfo(request.signInfo());
        if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatus(id, TaskType.SIGN, TaskStatus.PENDING)) {
            changeStatus(contract, ContractStatus.SIGNED, operator, "合同签订完成");
        }
        return detail(id, operator);
    }

    @Transactional
    public ContractView update(Long id, ContractCreateRequest request, SysUser operator) {
        Contract contract = getContract(id);
        ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.DRAFT, ContractStatus.COUNTERSIGNED, ContractStatus.REJECTED);
        if (!contract.getDrafter().getId().equals(operator.getId())) {
            throw ApiException.forbidden("只有起草人可以修改合同");
        }
        contract.setName(request.name());
        contract.setBeginDate(request.beginDate());
        contract.setEndDate(request.endDate());
        contract.setContent(request.content());
        if (!contract.getCustomer().getId().equals(request.customerId())) {
            Customer customer = customerRepository.findById(request.customerId())
                    .filter(c -> !c.isDeleted())
                    .orElseThrow(() -> ApiException.notFound("客户不存在"));
            contract.setCustomer(customer);
        }
        contractRepository.save(contract);
        recordState(contract, contract.getStatus(), contract.getStatus(), operator, "修改合同信息");
        return ContractView.from(contract);
    }

    @Transactional
    public void delete(Long id, SysUser operator) {
        Contract contract = getContract(id);
        if (contract.getStatus() != ContractStatus.DRAFT && contract.getStatus() != ContractStatus.CANCELLED) {
            throw ApiException.conflict("只能删除草稿或已取消的合同");
        }
        contract.setDeleted(true);
        contractRepository.save(contract);
        recordState(contract, contract.getStatus(), contract.getStatus(), operator, "删除合同");
    }

    @Transactional
    public void cancel(Long id, SysUser operator) {
        Contract contract = getContract(id);
        if (contract.getStatus() == ContractStatus.SIGNED || contract.getStatus() == ContractStatus.CANCELLED) {
            throw ApiException.conflict("当前合同状态不允许取消");
        }
        completeRemainingPendingTasks(id, "合同已取消，待办已关闭");
        changeStatus(contract, ContractStatus.CANCELLED, operator, "取消合同");
    }

    public Page<ContractStateHistory> logs(String keyword, int page, int size) {
        var pr = PageRequests.of(page, size);
        if (keyword == null || keyword.isEmpty()) {
            return contractRepository.findHistory(pr);
        }
        return contractRepository.findHistoryByKeyword(keyword, pr);
    }

    @Transactional
    public ContractDetailView resubmit(Long id, SysUser operator) {
        Contract contract = getContract(id);
        ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.REJECTED);
        List<ContractTask> approvalTasks = taskRepository.findByContractIdAndTaskType(contract.getId(), TaskType.APPROVAL);
        for (ContractTask task : approvalTasks) {
            task.setTaskStatus(TaskStatus.PENDING);
            task.setOpinion(null);
            task.setOperatedAt(null);
        }
        taskRepository.saveAll(approvalTasks);
        changeStatus(contract, ContractStatus.FINALIZED, operator, "重新提交审批");
        return detail(id, operator);
    }

    public byte[] exportLogs(String keyword) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("﻿"); // BOM for Excel UTF-8
        sb.append("时间,合同编号,合同名称,操作人,原状态,新状态,备注\n");
        List<ContractStateHistory> list;
        if (keyword == null || keyword.isEmpty()) {
            list = contractRepository.findHistory(org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        } else {
            list = contractRepository.findHistoryByKeyword(keyword, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        }
        for (ContractStateHistory h : list) {
            sb.append(h.getCreatedAt()).append(',');
            sb.append(escapeCsv(h.getContract().getContractNo())).append(',');
            sb.append(escapeCsv(h.getContract().getName())).append(',');
            sb.append(escapeCsv(h.getOperator().getDisplayName())).append(',');
            sb.append(h.getFromStatus() != null ? h.getFromStatus() : "-").append(',');
            sb.append(h.getToStatus()).append(',');
            sb.append(escapeCsv(h.getRemark() != null ? h.getRemark() : "")).append('\n');
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    public Map<String, Object> getStatistics() {
        long total = contractRepository.countByDeletedFalse();
        long draft = contractRepository.countByDeletedFalseAndStatus(ContractStatus.DRAFT);
        long assigned = contractRepository.countByDeletedFalseAndStatus(ContractStatus.ASSIGNED);
        long signed = contractRepository.countByDeletedFalseAndStatus(ContractStatus.SIGNED);
        long rejected = contractRepository.countByDeletedFalseAndStatus(ContractStatus.REJECTED);
        long pendingTasks = taskRepository.countByTaskStatus(TaskStatus.PENDING);
        return Map.of(
                "total", total,
                "draft", draft,
                "assigned", assigned,
                "signed", signed,
                "rejected", rejected,
                "pendingTasks", pendingTasks
        );
    }

    public Map<String, Object> getStatistics(SysUser user) {
        if (hasPermission(user, "contract:query")) {
            return getStatistics();
        }
        List<ContractView> related = list("", "", 1, 10000, user).getContent();
        return Map.of(
                "total", (long) related.size(),
                "draft", related.stream().filter(c -> c.status() == ContractStatus.DRAFT).count(),
                "assigned", related.stream().filter(c -> c.status() == ContractStatus.ASSIGNED).count(),
                "signed", related.stream().filter(c -> c.status() == ContractStatus.SIGNED).count(),
                "rejected", related.stream().filter(c -> c.status() == ContractStatus.REJECTED).count(),
                "pendingTasks", taskRepository.countByAssigneeAndTaskStatus(user, TaskStatus.PENDING)
        );
    }

    public Map<String, Object> getMyTaskStatistics(SysUser user) {
        return Map.of(
                "pendingTasks", taskRepository.countByAssigneeAndTaskStatus(user, TaskStatus.PENDING),
                "doneTasks", taskRepository.countByAssigneeAndTaskStatus(user, TaskStatus.DONE),
                "rejectedTasks", taskRepository.countByAssigneeAndTaskStatus(user, TaskStatus.REJECTED)
        );
    }

    public List<Map<String, Object>> getMonthlyStatistics() {
        return contractRepository.findAll().stream()
                .filter(contract -> !contract.isDeleted())
                .collect(java.util.stream.Collectors.groupingBy(
                        contract -> contract.getCreatedAt().getYear() + "-" + String.format("%02d", contract.getCreatedAt().getMonthValue()),
                        java.util.stream.Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.<String, Object>of("month", entry.getKey(), "count", entry.getValue()))
                .toList();
    }

    public Contract getContract(Long id) {
        return contractRepository.findById(id).filter(c -> !c.isDeleted())
                .orElseThrow(() -> ApiException.notFound("合同不存在"));
    }

    public void ensureCanViewContract(Long contractId, SysUser user) {
        ensureCanViewContract(getContract(contractId), user);
    }

    public void ensureCanModifyContract(Long contractId, SysUser user) {
        Contract contract = getContract(contractId);
        ensureCanViewContract(contract, user);
        ensureMutableContract(contract);
    }

    public boolean canViewContract(Contract contract, SysUser user) {
        return hasPermission(user, "contract:query")
                || contract.getDrafter().getId().equals(user.getId())
                || taskRepository.existsByContractIdAndAssigneeId(contract.getId(), user.getId())
                || (contract.getStatus() == ContractStatus.DRAFT && hasPermission(user, "contract:assign"));
    }

    public boolean hasPermission(SysUser user, String permissionCode) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> permissionCode.equals(permission.getPermissionCode()));
    }

    private ContractStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isEmpty()) {
            return null;
        }
        try {
            return ContractStatus.valueOf(statusStr);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("合同状态不合法: " + statusStr);
        }
    }

    private void ensureCanViewContract(Contract contract, SysUser user) {
        if (!canViewContract(contract, user)) {
            throw ApiException.forbidden("当前用户无权查看该合同");
        }
    }

    private void createAssignTasks(Contract contract) {
        userRepository.findEnabledUsersWithPermission("contract:assign").stream()
                .forEach(user -> createTask(contract, user.getId(), TaskType.ASSIGN, "contract:assign"));
    }

    private void validateAssignees(Contract contract, List<Long> userIds, String label) {
        if (new LinkedHashSet<>(userIds).size() != userIds.size()) {
            throw ApiException.conflict(label + "不能重复");
        }
        if (userIds.contains(contract.getDrafter().getId())) {
            throw ApiException.conflict(label + "不能包含起草人");
        }
    }

    private void createUniqueTasks(Contract contract, List<Long> userIds, TaskType taskType, String requiredPermission) {
        Set<Long> ids = new LinkedHashSet<>(userIds);
        ids.forEach(userId -> createTask(contract, userId, taskType, requiredPermission));
    }

    private void createTask(Contract contract, Long userId, TaskType taskType, String requiredPermission) {
        SysUser assignee = userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在: " + userId));
        if (assignee.getStatus() != UserStatus.ENABLED) {
            throw ApiException.conflict("用户已禁用，不能分配流程任务: " + assignee.getUsername());
        }
        boolean hasPermission = assignee.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .anyMatch(permission -> requiredPermission.equals(permission.getPermissionCode()));
        if (!hasPermission) {
            throw ApiException.conflict("用户缺少流程权限 " + requiredPermission + ": " + assignee.getUsername());
        }
        ContractTask task = new ContractTask();
        task.setContract(contract);
        task.setAssignee(assignee);
        task.setTaskType(taskType);
        taskRepository.save(task);
    }

    private void finishTask(Long contractId, SysUser operator, TaskType taskType, TaskStatus status, String opinion) {
        ContractTask task = taskRepository.findByContractIdAndAssigneeAndTaskTypeAndTaskStatus(contractId, operator, taskType, TaskStatus.PENDING)
                .orElseThrow(() -> ApiException.forbidden("当前用户没有该合同的待处理任务"));
        task.setTaskStatus(status);
        task.setOpinion(opinion);
        task.setOperatedAt(LocalDateTime.now());
    }

    private void completeRemainingPendingTasks(Long contractId, TaskType taskType, String opinion) {
        List<ContractTask> tasks = taskRepository.findByContractIdAndTaskTypeAndTaskStatus(contractId, taskType, TaskStatus.PENDING);
        for (ContractTask task : tasks) {
            task.setTaskStatus(TaskStatus.DONE);
            task.setOpinion(opinion);
            task.setOperatedAt(LocalDateTime.now());
        }
        taskRepository.saveAll(tasks);
    }

    private void completeRemainingPendingTasks(Long contractId, String opinion) {
        List<ContractTask> tasks = taskRepository.findByContractIdAndTaskStatus(contractId, TaskStatus.PENDING);
        for (ContractTask task : tasks) {
            task.setTaskStatus(TaskStatus.DONE);
            task.setOpinion(opinion);
            task.setOperatedAt(LocalDateTime.now());
        }
        taskRepository.saveAll(tasks);
    }

    private void ensureMutableContract(Contract contract) {
        if (contract.getStatus() == ContractStatus.CANCELLED) {
            throw ApiException.conflict("已取消合同不能继续操作");
        }
    }

    private void requireStatus(Contract contract, ContractStatus... statuses) {
        for (ContractStatus status : statuses) {
            if (contract.getStatus() == status) {
                return;
            }
        }
        throw ApiException.conflict("当前合同状态不允许该操作: " + contract.getStatus());
    }

    private void changeStatus(Contract contract, ContractStatus to, SysUser operator, String remark) {
        ContractStatus from = contract.getStatus();
        contract.setStatus(to);
        recordState(contract, from, to, operator, remark);
    }

    private void recordState(Contract contract, ContractStatus from, ContractStatus to, SysUser operator, String remark) {
        ContractStateHistory history = new ContractStateHistory();
        history.setContract(contract);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperator(operator);
        history.setRemark(remark);
        stateHistoryRepository.save(history);
        operationLogService.record(operator, "CONTRACT", remark, "CONTRACT", contract.getId(),
                contract.getContractNo() + " " + contract.getName() + " " + (from == null ? "-" : from) + " -> " + to);
    }
}
