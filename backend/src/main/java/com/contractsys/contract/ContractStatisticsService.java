package com.contractsys.contract;

import com.contractsys.contract.dto.ContractView;
import com.contractsys.user.SysUser;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ContractStatisticsService {
    private final ContractRepository contractRepository;
    private final ContractTaskRepository taskRepository;
    private final ContractQueryService queryService;
    private final ContractAccessGuard accessGuard;

    public ContractStatisticsService(ContractRepository contractRepository,
                                     ContractTaskRepository taskRepository,
                                     ContractQueryService queryService,
                                     ContractAccessGuard accessGuard) {
        this.contractRepository = contractRepository;
        this.taskRepository = taskRepository;
        this.queryService = queryService;
        this.accessGuard = accessGuard;
    }

    @Cacheable(cacheNames = "contractStats", key = "'global'")
    public Map<String, Object> getStatistics() {
        Map<ContractStatus, Long> byStatus = contractRepository.countByStatus().stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (ContractStatus) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long draft = byStatus.getOrDefault(ContractStatus.DRAFT, 0L);
        long assigned = byStatus.getOrDefault(ContractStatus.ASSIGNED, 0L);
        long signed = byStatus.getOrDefault(ContractStatus.SIGNED, 0L);
        long rejected = byStatus.getOrDefault(ContractStatus.REJECTED, 0L);
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
        if (accessGuard.hasPermission(user, "contract:query")) {
            return getStatistics();
        }
        List<ContractView> related = queryService.list("", "", 1, 10000, user).getContent();
        return Map.of(
                "total", (long) related.size(),
                "draft", related.stream().filter(contract -> contract.status() == ContractStatus.DRAFT).count(),
                "assigned", related.stream().filter(contract -> contract.status() == ContractStatus.ASSIGNED).count(),
                "signed", related.stream().filter(contract -> contract.status() == ContractStatus.SIGNED).count(),
                "rejected", related.stream().filter(contract -> contract.status() == ContractStatus.REJECTED).count(),
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

    @Cacheable(cacheNames = "monthlyStats", key = "'all'")
    public List<Map<String, Object>> getMonthlyStatistics() {
        return contractRepository.countByCreatedMonth().stream()
                .map(row -> {
                    int year = ((Number) row[0]).intValue();
                    int month = ((Number) row[1]).intValue();
                    return Map.<String, Object>of(
                            "month", year + "-" + String.format("%02d", month),
                            "count", ((Number) row[2]).longValue()
                    );
                })
                .toList();
    }
}
