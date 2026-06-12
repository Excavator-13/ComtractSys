package com.contractsys.contract;

import com.contractsys.common.ApiException;
import com.contractsys.common.BusinessNumberService;
import com.contractsys.common.event.ContractChangedEvent;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.contract.dto.*;
import com.contractsys.customer.Customer;
import com.contractsys.customer.CustomerRepository;
import com.contractsys.user.SysUser;
import com.contractsys.user.UserRepository;
import com.contractsys.user.UserStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ContractService {
    private final ContractRepository contractRepository;
    private final ContractTaskRepository taskRepository;
    private final ContractStateHistoryRepository stateHistoryRepository;
    private final ContractVersionRepository versionRepository;
    private final BusinessNumberService numberService;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ContractAccessGuard accessGuard;
    private final ContractQueryService queryService;
    private final ApplicationEventPublisher eventPublisher;

    public ContractService(ContractRepository contractRepository, ContractTaskRepository taskRepository,
                           ContractStateHistoryRepository stateHistoryRepository,
                           ContractVersionRepository versionRepository,
                           BusinessNumberService numberService,
                           CustomerRepository customerRepository, UserRepository userRepository,
                           ContractAccessGuard accessGuard,
                           ContractQueryService queryService,
                           ApplicationEventPublisher eventPublisher) {
        this.contractRepository = contractRepository;
        this.taskRepository = taskRepository;
        this.stateHistoryRepository = stateHistoryRepository;
        this.versionRepository = versionRepository;
        this.numberService = numberService;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.queryService = queryService;
        this.eventPublisher = eventPublisher;
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
        contract.setContractNo(numberService.temporaryNumber());
        contract.setName(request.name());
        contract.setCustomer(customer);
        contract.setBeginDate(request.beginDate());
        contract.setEndDate(request.endDate());
        contract.setContent(request.content());
        contract.setDrafter(operator);
        Contract saved = contractRepository.saveAndFlush(contract);
        saved.setContractNo(numberService.contractNo(saved.getId()));
        recordVersion(saved, operator, "起草合同");
        recordState(saved, null, ContractStatus.DRAFT, operator, "起草合同");
        createAssignTasks(saved);
        eventPublisher.publishEvent(new ContractChangedEvent(saved.getId()));
        return ContractView.from(saved);
    }

    @Transactional
    public ContractDetailView assign(Long id, AssignRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.DRAFT);
        finishTask(id, operator, TaskType.ASSIGN, TaskStatus.DONE, "已完成分配");
        completeRemainingPendingTasks(id, TaskType.ASSIGN, "其他分配待办已关闭");
        validateAssignees(contract, operator, request.countersignUserIds(), "会签人员", "contract:countersign");
        validateAssignees(contract, operator, request.approvalUserIds(), "审批人员", "contract:approve");
        if (request.signUserId().equals(contract.getDrafter().getId())) {
            throw ApiException.conflict("签订人员不能是起草人");
        }
        validateAssignee(contract, operator, request.signUserId(), "签订人员", "contract:sign");
        createUniqueTasks(contract, request.countersignUserIds(), TaskType.COUNTERSIGN, "contract:countersign");
        rememberFutureAssignees(contract, request.approvalUserIds(), TaskType.APPROVAL, "contract:approve");
        rememberFutureAssignees(contract, List.of(request.signUserId()), TaskType.SIGN, "contract:sign");
        changeStatus(contract, ContractStatus.ASSIGNED, operator, "管理员分配合同流程人员");
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView countersign(Long id, OpinionRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.ASSIGNED);
        finishTask(id, operator, TaskType.COUNTERSIGN, TaskStatus.DONE, request.opinion());
        if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatusAndRound(id, TaskType.COUNTERSIGN, TaskStatus.PENDING, contract.getCurrentRound())) {
            changeStatus(contract, ContractStatus.COUNTERSIGNED, operator, "全部会签完成");
            createTask(contract, contract.getDrafter().getId(), TaskType.FINALIZE, null);
        }
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView finalizeContract(Long id, FinalizeRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.COUNTERSIGNED);
        if (!contract.getDrafter().getId().equals(operator.getId())) {
            throw ApiException.forbidden("只有起草人可以定稿");
        }
        finishTask(id, operator, TaskType.FINALIZE, TaskStatus.DONE, "起草人定稿");
        contract.setContent(request.content());
        recordVersion(contract, operator, "起草人定稿");
        createPendingTasksFromAssignees(contract, TaskType.APPROVAL, "contract:approve");
        changeStatus(contract, ContractStatus.FINALIZED, operator, "起草人定稿");
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView returnContract(Long id, ReturnRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        TaskType currentTaskType = currentReturnableTaskType(contract);
        if ("FINALIZE".equals(request.targetStage()) && contract.getStatus() == ContractStatus.ASSIGNED) {
            throw ApiException.conflict("会签阶段只能打回至重新起草");
        }
        ContractTask task = taskRepository.findByContractIdAndAssigneeAndTaskTypeAndTaskStatusAndRound(
                        id, operator, currentTaskType, TaskStatus.PENDING, contract.getCurrentRound())
                .orElseThrow(() -> ApiException.forbidden("当前用户没有该合同的待处理任务"));
        task.setTaskStatus(TaskStatus.REJECTED);
        task.setOpinion("打回至" + returnTargetLabel(request.targetStage()) + "：" + request.opinion());
        task.setOperatedAt(LocalDateTime.now());
        supersedeCurrentRoundPendingTasks(contract, "合同已被打回，当前轮待办已封存");
        contract.setReturnTargetStage(request.targetStage());
        changeStatus(contract, ContractStatus.RETURNED, operator,
                "第 " + contract.getCurrentRound() + " 轮打回至" + returnTargetLabel(request.targetStage()));
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView resume(Long id, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.RETURNED);
        if (!contract.getDrafter().getId().equals(operator.getId())) {
            throw ApiException.forbidden("只有起草人可以恢复打回合同");
        }
        String target = contract.getReturnTargetStage();
        if (target == null || target.isBlank()) {
            throw ApiException.conflict("缺少打回目标，不能恢复");
        }
        int previousRound = contract.getCurrentRound();
        contract.setCurrentRound(previousRound + 1);
        contract.setReturnTargetStage(null);
        if ("DRAFT".equals(target)) {
            cloneAssigneeTasksForNewRound(contract, previousRound, TaskType.COUNTERSIGN, "contract:countersign", true);
            cloneAssigneeTasksForNewRound(contract, previousRound, TaskType.APPROVAL, "contract:approve", false);
            cloneAssigneeTasksForNewRound(contract, previousRound, TaskType.SIGN, "contract:sign", false);
            changeStatus(contract, ContractStatus.ASSIGNED, operator,
                    "第 " + contract.getCurrentRound() + " 轮重新起草后恢复会签");
        } else if ("FINALIZE".equals(target)) {
            createTask(contract, contract.getDrafter().getId(), TaskType.FINALIZE, null);
            cloneAssigneeTasksForNewRound(contract, previousRound, TaskType.COUNTERSIGN, "contract:countersign", false);
            cloneAssigneeTasksForNewRound(contract, previousRound, TaskType.APPROVAL, "contract:approve", false);
            cloneAssigneeTasksForNewRound(contract, previousRound, TaskType.SIGN, "contract:sign", false);
            changeStatus(contract, ContractStatus.COUNTERSIGNED, operator,
                    "第 " + contract.getCurrentRound() + " 轮恢复至重新定稿");
        } else {
            throw ApiException.conflict("不支持的打回目标: " + target);
        }
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView recall(Long id, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.ASSIGNED);
        if (!contract.getDrafter().getId().equals(operator.getId())) {
            throw ApiException.forbidden("只有起草人可以撤回合同");
        }
        if (hasCompletedTask(contract, TaskType.COUNTERSIGN)) {
            throw ApiException.conflict("已有会签任务完成，不能撤回合同");
        }
        supersedeCurrentRoundPendingTasks(contract, "起草人撤回合同，当前轮待办已封存");
        contract.setCurrentRound(contract.getCurrentRound() + 1);
        contract.setReturnTargetStage(null);
        createAssignTasks(contract);
        changeStatus(contract, ContractStatus.DRAFT, operator, "起草人撤回合同");
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView withdrawTask(Long id, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        List<ContractTask> operated = taskRepository.findOperatedTasks(
                id, operator, contract.getCurrentRound(), List.of(TaskStatus.DONE, TaskStatus.REJECTED));
        if (operated.isEmpty()) {
            throw ApiException.conflict("没有可撤回的已处理任务");
        }
        ContractTask task = operated.stream()
                .filter(t -> t.getOperatedAt() != null)
                .max(Comparator.comparing(ContractTask::getOperatedAt))
                .orElseThrow(() -> ApiException.conflict("没有可撤回的已处理任务"));
        ensureWithdrawAllowed(contract, task);
        task.setTaskStatus(TaskStatus.PENDING);
        task.setOpinion(null);
        task.setOperatedAt(null);
        rewindStatusForWithdraw(contract, task, operator);
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView approve(Long id, ApproveRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.FINALIZED);
        TaskStatus taskStatus = request.result() == ApproveResult.APPROVED ? TaskStatus.DONE : TaskStatus.REJECTED;
        finishTask(id, operator, TaskType.APPROVAL, taskStatus, request.opinion());
        if (taskStatus == TaskStatus.REJECTED) {
            supersedeCurrentRoundPendingTasksByType(contract, TaskType.APPROVAL, "审批已被其他人拒绝，当前轮审批待办已封存");
            changeStatus(contract, ContractStatus.REJECTED, operator, "审批拒绝");
        } else if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatusAndRound(id, TaskType.APPROVAL, TaskStatus.PENDING, contract.getCurrentRound())) {
            createPendingTasksFromAssignees(contract, TaskType.SIGN, "contract:sign");
            changeStatus(contract, ContractStatus.APPROVED, operator, "全部审批通过");
        }
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView sign(Long id, SignRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.APPROVED);
        finishTask(id, operator, TaskType.SIGN, TaskStatus.DONE, request.signInfo());
        contract.setSignedDate(request.signedDate());
        contract.setSignInfo(request.signInfo());
        if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatusAndRound(id, TaskType.SIGN, TaskStatus.PENDING, contract.getCurrentRound())) {
            changeStatus(contract, ContractStatus.SIGNED, operator, "合同签订完成");
        }
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractView update(Long id, ContractCreateRequest request, SysUser operator) {
        if (request.endDate().isBefore(request.beginDate())) {
            throw ApiException.badRequest("结束日期不能早于开始日期");
        }
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.DRAFT, ContractStatus.COUNTERSIGNED, ContractStatus.REJECTED, ContractStatus.RETURNED);
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
        recordVersion(contract, operator, "修改合同信息");
        recordState(contract, contract.getStatus(), contract.getStatus(), operator, "修改合同信息");
        return ContractView.from(contract);
    }

    @Transactional
    public void delete(Long id, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        if (contract.getStatus() != ContractStatus.DRAFT && contract.getStatus() != ContractStatus.CANCELLED) {
            throw ApiException.conflict("只能删除草稿或已取消的合同");
        }
        completeRemainingPendingTasks(id, "合同已删除，待办已关闭");
        contract.setDeleted(true);
        contractRepository.save(contract);
        recordState(contract, contract.getStatus(), contract.getStatus(), operator, "删除合同");
        eventPublisher.publishEvent(new ContractChangedEvent(contract.getId()));
    }

    @Transactional
    public void cancel(Long id, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        if (contract.getStatus() == ContractStatus.SIGNED || contract.getStatus() == ContractStatus.CANCELLED) {
            throw ApiException.conflict("当前合同状态不允许取消");
        }
        completeRemainingPendingTasks(id, "合同已取消，待办已关闭");
        changeStatus(contract, ContractStatus.CANCELLED, operator, "取消合同");
    }

    @Transactional
    public ContractDetailView resubmit(Long id, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.REJECTED);
        if (!contract.getDrafter().getId().equals(operator.getId())) {
            throw ApiException.forbidden("只有起草人可以重新提交");
        }
        int previousRound = contract.getCurrentRound();
        contract.setCurrentRound(previousRound + 1);
        cloneAssigneeTasksForNewRound(contract, previousRound, TaskType.COUNTERSIGN, "contract:countersign", false);
        cloneAssigneeTasksForNewRound(contract, previousRound, TaskType.APPROVAL, "contract:approve", true);
        cloneAssigneeTasksForNewRound(contract, previousRound, TaskType.SIGN, "contract:sign", false);
        changeStatus(contract, ContractStatus.FINALIZED, operator, "重新提交审批");
        return queryService.detail(id, operator);
    }

    private void createAssignTasks(Contract contract) {
        userRepository.findEnabledUsersWithPermission("contract:assign").stream()
                .forEach(user -> createTask(contract, user.getId(), TaskType.ASSIGN, "contract:assign"));
    }

    private void validateAssignees(Contract contract, SysUser operator, List<Long> userIds, String label, String requiredPermission) {
        if (new LinkedHashSet<>(userIds).size() != userIds.size()) {
            throw ApiException.conflict(label + "不能重复");
        }
        userIds.forEach(userId -> validateAssignee(contract, operator, userId, label, requiredPermission));
    }

    private void validateAssignee(Contract contract, SysUser operator, Long userId, String label, String requiredPermission) {
        if (userId.equals(contract.getDrafter().getId())) {
            throw ApiException.conflict(label + "不能包含起草人");
        }
        if (userId.equals(operator.getId())) {
            throw ApiException.conflict(label + "不能包含当前分配人");
        }
        SysUser assignee = userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在: " + userId));
        if (assignee.isBuiltInAdmin()) {
            throw ApiException.conflict(label + "不能包含内置管理员");
        }
        if (assignee.getStatus() != UserStatus.ENABLED) {
            throw ApiException.conflict("用户已禁用，不能分配流程任务: " + assignee.getUsername());
        }
        if (requiredPermission != null && !assignee.hasPermission(requiredPermission)) {
            throw ApiException.conflict("用户缺少流程权限 " + requiredPermission + ": " + assignee.getUsername());
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
        if (requiredPermission != null && !assignee.hasPermission(requiredPermission)) {
            throw ApiException.conflict("用户缺少流程权限 " + requiredPermission + ": " + assignee.getUsername());
        }
        ContractTask task = new ContractTask();
        task.setContract(contract);
        task.setAssignee(assignee);
        task.setTaskType(taskType);
        task.setRound(contract.getCurrentRound());
        taskRepository.save(task);
    }

    private void rememberFutureAssignees(Contract contract, List<Long> userIds, TaskType taskType, String requiredPermission) {
        Set<Long> ids = new LinkedHashSet<>(userIds);
        ids.forEach(userId -> createTask(contract, userId, taskType, requiredPermission, TaskStatus.SUPERSEDED,
                "已分配，等待前置环节完成后激活"));
    }

    private void createTask(Contract contract, Long userId, TaskType taskType, String requiredPermission,
                            TaskStatus status, String opinion) {
        SysUser assignee = userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> ApiException.notFound("用户不存在: " + userId));
        if (assignee.getStatus() != UserStatus.ENABLED) {
            throw ApiException.conflict("用户已禁用，不能分配流程任务: " + assignee.getUsername());
        }
        if (requiredPermission != null && !assignee.hasPermission(requiredPermission)) {
            throw ApiException.conflict("用户缺少流程权限 " + requiredPermission + ": " + assignee.getUsername());
        }
        ContractTask task = new ContractTask();
        task.setContract(contract);
        task.setAssignee(assignee);
        task.setTaskType(taskType);
        task.setTaskStatus(status);
        task.setOpinion(opinion);
        task.setRound(contract.getCurrentRound());
        if (status != TaskStatus.PENDING) {
            task.setOperatedAt(LocalDateTime.now());
        }
        taskRepository.save(task);
    }

    private void createPendingTasksFromAssignees(Contract contract, TaskType taskType, String requiredPermission) {
        taskRepository.findByContractIdAndTaskTypeAndRound(contract.getId(), taskType, contract.getCurrentRound()).stream()
                .map(task -> task.getAssignee().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .forEach(userId -> createTask(contract, userId, taskType, requiredPermission));
    }

    private void finishTask(Long contractId, SysUser operator, TaskType taskType, TaskStatus status, String opinion) {
        Contract contract = accessGuard.getContract(contractId);
        ContractTask task = taskRepository.findByContractIdAndAssigneeAndTaskTypeAndTaskStatusAndRound(
                        contractId, operator, taskType, TaskStatus.PENDING, contract.getCurrentRound())
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

    private void supersedeCurrentRoundPendingTasks(Contract contract, String opinion) {
        List<ContractTask> tasks = taskRepository.findByContractIdAndRoundAndTaskStatus(
                contract.getId(), contract.getCurrentRound(), TaskStatus.PENDING);
        for (ContractTask task : tasks) {
            task.setTaskStatus(TaskStatus.SUPERSEDED);
            task.setOpinion(opinion);
            task.setOperatedAt(LocalDateTime.now());
        }
        taskRepository.saveAll(tasks);
    }

    private void cloneAssigneeTasksForNewRound(Contract contract, int previousRound, TaskType taskType,
                                               String requiredPermission, boolean activate) {
        taskRepository.findByContractIdAndTaskTypeAndRound(contract.getId(), taskType, previousRound).stream()
                .map(task -> task.getAssignee().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .forEach(userId -> {
                    if (activate) {
                        createTask(contract, userId, taskType, requiredPermission);
                    } else {
                        createTask(contract, userId, taskType, requiredPermission, TaskStatus.SUPERSEDED,
                                "已分配，等待前置环节完成后激活");
                    }
                });
    }

    private TaskType currentReturnableTaskType(Contract contract) {
        return switch (contract.getStatus()) {
            case ASSIGNED -> TaskType.COUNTERSIGN;
            case FINALIZED -> TaskType.APPROVAL;
            case APPROVED -> TaskType.SIGN;
            default -> throw ApiException.conflict("当前合同状态不允许打回: " + contract.getStatus());
        };
    }

    private String returnTargetLabel(String targetStage) {
        return "DRAFT".equals(targetStage) ? "重新起草" : "重新定稿";
    }

    private boolean hasCompletedTask(Contract contract, TaskType taskType) {
        return taskRepository.findByContractIdAndTaskTypeAndRound(contract.getId(), taskType, contract.getCurrentRound()).stream()
                .anyMatch(task -> task.getTaskStatus() == TaskStatus.DONE || task.getTaskStatus() == TaskStatus.REJECTED);
    }

    private void ensureWithdrawAllowed(Contract contract, ContractTask task) {
        if (task.getTaskType() == TaskType.SIGN || contract.getStatus() == ContractStatus.SIGNED) {
            throw ApiException.conflict("签订完成后不能撤回任务");
        }
        if (task.getTaskType() == TaskType.COUNTERSIGN && hasCompletedTask(contract, TaskType.FINALIZE)) {
            throw ApiException.conflict("定稿已完成，不能撤回会签");
        }
        if (task.getTaskType() == TaskType.FINALIZE && hasCompletedTask(contract, TaskType.APPROVAL)) {
            throw ApiException.conflict("审批已开始，不能撤回定稿");
        }
        if (task.getTaskType() == TaskType.APPROVAL && hasCompletedTask(contract, TaskType.SIGN)) {
            throw ApiException.conflict("签订已开始，不能撤回审批");
        }
    }

    private void rewindStatusForWithdraw(Contract contract, ContractTask task, SysUser operator) {
        switch (task.getTaskType()) {
            case COUNTERSIGN -> {
                supersedeCurrentRoundPendingTasksByType(contract, TaskType.FINALIZE, "会签撤回，定稿待办已封存");
                changeStatus(contract, ContractStatus.ASSIGNED, operator, "撤回会签意见");
            }
            case FINALIZE -> {
                supersedeCurrentRoundPendingTasksByType(contract, TaskType.APPROVAL, "定稿撤回，审批待办已封存");
                changeStatus(contract, ContractStatus.COUNTERSIGNED, operator, "撤回定稿");
            }
            case APPROVAL -> {
                supersedeCurrentRoundPendingTasksByType(contract, TaskType.SIGN, "审批撤回，签订待办已封存");
                changeStatus(contract, ContractStatus.FINALIZED, operator, "撤回审批意见");
            }
            default -> throw ApiException.conflict("该任务类型不支持撤回");
        }
    }

    private void supersedeCurrentRoundPendingTasksByType(Contract contract, TaskType taskType, String opinion) {
        List<ContractTask> tasks = taskRepository.findByContractIdAndTaskTypeAndTaskStatus(contract.getId(), taskType, TaskStatus.PENDING).stream()
                .filter(task -> task.getRound() == contract.getCurrentRound())
                .toList();
        for (ContractTask task : tasks) {
            task.setTaskStatus(TaskStatus.SUPERSEDED);
            task.setOpinion(opinion);
            task.setOperatedAt(LocalDateTime.now());
        }
        taskRepository.saveAll(tasks);
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
        eventPublisher.publishEvent(new ContractChangedEvent(contract.getId()));
        eventPublisher.publishEvent(new OperationLogEvent(operator, "CONTRACT", remark, "CONTRACT", contract.getId(),
                contract.getContractNo() + " " + contract.getName() + " " + (from == null ? "-" : from) + " -> " + to));
    }

    private void recordVersion(Contract contract, SysUser operator, String remark) {
        ContractVersion version = new ContractVersion();
        version.setContract(contract);
        version.setVersionNo((int) versionRepository.countByContractId(contract.getId()) + 1);
        version.setName(contract.getName());
        version.setContent(contract.getContent());
        version.setOperator(operator);
        version.setRemark(remark);
        versionRepository.save(version);
    }

}
