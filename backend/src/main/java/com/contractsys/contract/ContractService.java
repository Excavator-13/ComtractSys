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
        return ContractView.from(saved);
    }

    @Transactional
    public ContractDetailView assign(Long id, AssignRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
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
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView countersign(Long id, OpinionRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.ASSIGNED);
        finishTask(id, operator, TaskType.COUNTERSIGN, TaskStatus.DONE, request.opinion());
        if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatus(id, TaskType.COUNTERSIGN, TaskStatus.PENDING)) {
            changeStatus(contract, ContractStatus.COUNTERSIGNED, operator, "全部会签完成");
            createTask(contract, contract.getDrafter().getId(), TaskType.FINALIZE, "contract:update");
        }
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractDetailView finalizeContract(Long id, FinalizeRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
        requireStatus(contract, ContractStatus.COUNTERSIGNED, ContractStatus.REJECTED);
        if (!contract.getDrafter().getId().equals(operator.getId())) {
            throw ApiException.forbidden("只有起草人可以定稿");
        }
        finishTask(id, operator, TaskType.FINALIZE, TaskStatus.DONE, "起草人定稿");
        contract.setContent(request.content());
        recordVersion(contract, operator, "起草人定稿");
        changeStatus(contract, ContractStatus.FINALIZED, operator, "起草人定稿");
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
            changeStatus(contract, ContractStatus.REJECTED, operator, "审批拒绝");
        } else if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatus(id, TaskType.APPROVAL, TaskStatus.PENDING)) {
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
        if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatus(id, TaskType.SIGN, TaskStatus.PENDING)) {
            changeStatus(contract, ContractStatus.SIGNED, operator, "合同签订完成");
        }
        return queryService.detail(id, operator);
    }

    @Transactional
    public ContractView update(Long id, ContractCreateRequest request, SysUser operator) {
        Contract contract = accessGuard.getContractForUpdate(id);
        accessGuard.ensureMutableContract(contract);
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
        List<ContractTask> approvalTasks = taskRepository.findByContractIdAndTaskType(contract.getId(), TaskType.APPROVAL);
        for (ContractTask task : approvalTasks) {
            task.setTaskStatus(TaskStatus.PENDING);
            task.setOpinion(null);
            task.setOperatedAt(null);
        }
        taskRepository.saveAll(approvalTasks);
        changeStatus(contract, ContractStatus.FINALIZED, operator, "重新提交审批");
        return queryService.detail(id, operator);
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
        if (!assignee.hasPermission(requiredPermission)) {
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
