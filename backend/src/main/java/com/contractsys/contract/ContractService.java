package com.contractsys.contract;

import com.contractsys.common.ApiException;
import com.contractsys.common.PageRequests;
import com.contractsys.contract.dto.*;
import com.contractsys.customer.Customer;
import com.contractsys.customer.CustomerRepository;
import com.contractsys.user.SysUser;
import com.contractsys.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ContractService {
    private final ContractRepository contractRepository;
    private final ContractTaskRepository taskRepository;
    private final ContractStateHistoryRepository stateHistoryRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public ContractService(ContractRepository contractRepository, ContractTaskRepository taskRepository,
                           ContractStateHistoryRepository stateHistoryRepository, CustomerRepository customerRepository,
                           UserRepository userRepository) {
        this.contractRepository = contractRepository;
        this.taskRepository = taskRepository;
        this.stateHistoryRepository = stateHistoryRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    public Page<ContractView> list(String keyword, String statusStr, int page, int size) {
        ContractStatus status = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                status = ContractStatus.valueOf(statusStr);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("合同状态不合法: " + statusStr);
            }
        }
        return contractRepository.search(
                keyword == null ? "" : keyword, status,
                PageRequests.of(page, size)
        ).map(ContractView::from);
    }

    public ContractDetailView detail(Long id) {
        Contract contract = getContract(id);
        List<TaskView> tasks = taskRepository.findByContractIdOrderByCreatedAtAsc(id).stream().map(TaskView::from).toList();
        return new ContractDetailView(ContractView.from(contract), tasks);
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
        return ContractView.from(saved);
    }

    @Transactional
    public ContractDetailView assign(Long id, AssignRequest request, SysUser operator) {
        Contract contract = getContract(id);
        requireStatus(contract, ContractStatus.DRAFT);
        request.countersignUserIds().forEach(userId -> createTask(contract, userId, TaskType.COUNTERSIGN));
        request.approvalUserIds().forEach(userId -> createTask(contract, userId, TaskType.APPROVAL));
        request.signUserIds().forEach(userId -> createTask(contract, userId, TaskType.SIGN));
        changeStatus(contract, ContractStatus.ASSIGNED, operator, "管理员分配合同流程人员");
        return detail(id);
    }

    public List<TaskView> myTasks(SysUser user) {
        return taskRepository.findByAssigneeAndTaskStatus(user, TaskStatus.PENDING).stream().map(TaskView::from).toList();
    }

    @Transactional
    public ContractDetailView countersign(Long id, OpinionRequest request, SysUser operator) {
        Contract contract = getContract(id);
        requireStatus(contract, ContractStatus.ASSIGNED);
        finishTask(id, operator, TaskType.COUNTERSIGN, TaskStatus.DONE, request.opinion());
        if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatus(id, TaskType.COUNTERSIGN, TaskStatus.PENDING)) {
            changeStatus(contract, ContractStatus.COUNTERSIGNED, operator, "全部会签完成");
        }
        return detail(id);
    }

    @Transactional
    public ContractDetailView finalizeContract(Long id, FinalizeRequest request, SysUser operator) {
        Contract contract = getContract(id);
        requireStatus(contract, ContractStatus.COUNTERSIGNED, ContractStatus.REJECTED);
        if (!contract.getDrafter().getId().equals(operator.getId())) {
            throw ApiException.forbidden("只有起草人可以定稿");
        }
        contract.setContent(request.content());
        changeStatus(contract, ContractStatus.FINALIZED, operator, "起草人定稿");
        return detail(id);
    }

    @Transactional
    public ContractDetailView approve(Long id, ApproveRequest request, SysUser operator) {
        Contract contract = getContract(id);
        requireStatus(contract, ContractStatus.FINALIZED);
        TaskStatus taskStatus = request.result() == ApproveResult.APPROVED ? TaskStatus.DONE : TaskStatus.REJECTED;
        finishTask(id, operator, TaskType.APPROVAL, taskStatus, request.opinion());
        if (taskStatus == TaskStatus.REJECTED) {
            changeStatus(contract, ContractStatus.REJECTED, operator, "审批拒绝");
        } else if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatus(id, TaskType.APPROVAL, TaskStatus.PENDING)) {
            changeStatus(contract, ContractStatus.APPROVED, operator, "全部审批通过");
        }
        return detail(id);
    }

    @Transactional
    public ContractDetailView sign(Long id, SignRequest request, SysUser operator) {
        Contract contract = getContract(id);
        requireStatus(contract, ContractStatus.APPROVED);
        finishTask(id, operator, TaskType.SIGN, TaskStatus.DONE, request.signInfo());
        contract.setSignedDate(request.signedDate());
        contract.setSignInfo(request.signInfo());
        if (!taskRepository.existsByContractIdAndTaskTypeAndTaskStatus(id, TaskType.SIGN, TaskStatus.PENDING)) {
            changeStatus(contract, ContractStatus.SIGNED, operator, "合同签订完成");
        }
        return detail(id);
    }

    @Transactional
    public ContractView update(Long id, ContractCreateRequest request, SysUser operator) {
        Contract contract = getContract(id);
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
        requireStatus(contract, ContractStatus.REJECTED);
        // Reset rejected approval tasks back to PENDING
        List<ContractTask> approvalTasks = taskRepository.findByContractIdAndTaskType(contract.getId(), TaskType.APPROVAL);
        for (ContractTask task : approvalTasks) {
            if (task.getTaskStatus() == TaskStatus.REJECTED) {
                task.setTaskStatus(TaskStatus.PENDING);
                task.setOpinion(null);
                task.setOperatedAt(null);
            }
        }
        taskRepository.saveAll(approvalTasks);
        changeStatus(contract, ContractStatus.FINALIZED, operator, "重新提交审批");
        return detail(id);
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

    public Contract getContract(Long id) {
        return contractRepository.findById(id).filter(c -> !c.isDeleted())
                .orElseThrow(() -> ApiException.notFound("合同不存在"));
    }

    private void createTask(Contract contract, Long userId, TaskType taskType) {
        SysUser assignee = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("用户不存在: " + userId));
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
    }
}
