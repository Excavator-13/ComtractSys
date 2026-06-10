package com.contractsys.contract;

import com.contractsys.common.ApiException;
import com.contractsys.common.PageRequests;
import com.contractsys.contract.dto.ContractDetailView;
import com.contractsys.contract.dto.ContractTemplateView;
import com.contractsys.contract.dto.ContractTimelineView;
import com.contractsys.contract.dto.ContractVersionView;
import com.contractsys.contract.dto.ContractView;
import com.contractsys.contract.dto.TaskView;
import com.contractsys.user.SysUser;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class ContractQueryService {
    private final ContractRepository contractRepository;
    private final ContractTaskRepository taskRepository;
    private final ContractStateHistoryRepository stateHistoryRepository;
    private final ContractTemplateRepository templateRepository;
    private final ContractVersionRepository versionRepository;
    private final ContractAccessGuard accessGuard;

    public ContractQueryService(ContractRepository contractRepository,
                                ContractTaskRepository taskRepository,
                                ContractStateHistoryRepository stateHistoryRepository,
                                ContractTemplateRepository templateRepository,
                                ContractVersionRepository versionRepository,
                                ContractAccessGuard accessGuard) {
        this.contractRepository = contractRepository;
        this.taskRepository = taskRepository;
        this.stateHistoryRepository = stateHistoryRepository;
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.accessGuard = accessGuard;
    }

    public Page<ContractView> list(String keyword, String statusStr, int page, int size, SysUser user) {
        ContractStatus status = parseStatus(statusStr);
        return contractRepository.searchRelated(
                keyword == null ? "" : keyword,
                status,
                user.getId(),
                accessGuard.hasPermission(user, "contract:assign"),
                ContractStatus.DRAFT,
                PageRequests.of(page, size)
        ).map(ContractView::from);
    }

    public Page<ContractView> advancedList(String keyword, String statusStr, Long customerId, Long drafterId,
                                           LocalDate beginFrom, LocalDate beginTo, LocalDate endFrom, LocalDate endTo,
                                           int page, int size, SysUser user) {
        ContractStatus status = parseStatus(statusStr);
        return contractRepository.advancedSearchRelated(
                keyword == null ? "" : keyword,
                status, customerId, drafterId, beginFrom, beginTo, endFrom, endTo,
                user.getId(),
                accessGuard.hasPermission(user, "contract:assign"),
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

    public Page<ContractView> advancedQuery(String keyword, String statusStr, Long customerId, Long drafterId,
                                            LocalDate beginFrom, LocalDate beginTo, LocalDate endFrom, LocalDate endTo,
                                            int page, int size) {
        ContractStatus status = parseStatus(statusStr);
        return contractRepository.advancedSearch(
                keyword == null ? "" : keyword,
                status, customerId, drafterId, beginFrom, beginTo, endFrom, endTo,
                PageRequests.of(page, size)
        ).map(ContractView::from);
    }

    public Page<ContractView> exportAdvancedQuery(String keyword, String statusStr, Long customerId, Long drafterId,
                                                  LocalDate beginFrom, LocalDate beginTo, LocalDate endFrom, LocalDate endTo) {
        ContractStatus status = parseStatus(statusStr);
        return contractRepository.advancedSearch(
                keyword == null ? "" : keyword,
                status, customerId, drafterId, beginFrom, beginTo, endFrom, endTo,
                PageRequest.of(0, 10000)
        ).map(ContractView::from);
    }

    public Page<ContractView> exportAdvancedList(String keyword, String statusStr, Long customerId, Long drafterId,
                                                 LocalDate beginFrom, LocalDate beginTo, LocalDate endFrom, LocalDate endTo,
                                                 SysUser user) {
        ContractStatus status = parseStatus(statusStr);
        return contractRepository.advancedSearchRelated(
                keyword == null ? "" : keyword,
                status, customerId, drafterId, beginFrom, beginTo, endFrom, endTo,
                user.getId(),
                accessGuard.hasPermission(user, "contract:assign"),
                ContractStatus.DRAFT,
                PageRequest.of(0, 10000)
        ).map(ContractView::from);
    }

    public ContractDetailView detail(Long id, SysUser user) {
        Contract contract = accessGuard.getContract(id);
        accessGuard.ensureCanViewContract(contract, user);
        List<TaskView> tasks = taskRepository.findByContractIdOrderByCreatedAtAsc(id).stream().map(TaskView::from).toList();
        return new ContractDetailView(ContractView.from(contract), tasks);
    }

    public Map<String, Object> process(Long id, SysUser user) {
        Contract contract = accessGuard.getContract(id);
        accessGuard.ensureCanViewContract(contract, user);
        return Map.of(
                "contract", ContractView.from(contract),
                "tasks", taskRepository.findByContractIdOrderByCreatedAtAsc(id).stream().map(TaskView::from).toList(),
                "histories", stateHistoryRepository.findByContractIdOrderByCreatedAtAsc(id)
        );
    }

    public List<TaskView> myTasks(SysUser user) {
        return taskRepository.findByAssigneeAndTaskStatus(user, TaskStatus.PENDING).stream().map(TaskView::from).toList();
    }

    @Cacheable(cacheNames = "contractTemplates", key = "'enabled'")
    public List<ContractTemplateView> templates() {
        return templateRepository.findByEnabledTrueOrderByCreatedAtAsc().stream()
                .map(ContractTemplateView::from)
                .toList();
    }

    public List<ContractVersionView> versions(Long id, SysUser user) {
        accessGuard.ensureCanViewContract(id, user);
        return versionRepository.findByContractIdOrderByVersionNoDesc(id).stream()
                .map(ContractVersionView::from)
                .toList();
    }

    public List<ContractTimelineView> timeline(Long id, SysUser user) {
        accessGuard.ensureCanViewContract(id, user);
        return stateHistoryRepository.findByContractIdOrderByCreatedAtAsc(id).stream()
                .map(ContractTimelineView::from)
                .toList();
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
}
