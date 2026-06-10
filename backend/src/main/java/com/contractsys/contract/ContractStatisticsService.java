package com.contractsys.contract;

import com.contractsys.user.SysUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ContractStatisticsService {
    private final ContractRepository contractRepository;
    private final ContractTaskRepository taskRepository;
    private final ContractAccessGuard accessGuard;
    private final ObjectProvider<ContractStatisticsService> selfProvider;

    public ContractStatisticsService(ContractRepository contractRepository,
                                      ContractTaskRepository taskRepository,
                                      ContractAccessGuard accessGuard,
                                      ObjectProvider<ContractStatisticsService> selfProvider) {
        this.contractRepository = contractRepository;
        this.taskRepository = taskRepository;
        this.accessGuard = accessGuard;
        this.selfProvider = selfProvider;
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
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("draft", draft);
        stats.put("assigned", assigned);
        stats.put("signed", signed);
        stats.put("rejected", rejected);
        stats.put("pendingTasks", pendingTasks);
        return stats;
    }

    public Map<String, Object> getStatistics(SysUser user) {
        if (accessGuard.hasPermission(user, "contract:query")) {
            return selfProvider.getObject().getStatistics();
        }
        Map<ContractStatus, Long> byStatus = contractRepository.countRelatedByStatus(
                        user.getId(),
                        accessGuard.hasPermission(user, "contract:assign"),
                        ContractStatus.DRAFT
                ).stream()
                .collect(Collectors.toMap(
                        row -> (ContractStatus) row[0],
                        row -> ((Number) row[1]).longValue()
                ));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("draft", byStatus.getOrDefault(ContractStatus.DRAFT, 0L));
        stats.put("assigned", byStatus.getOrDefault(ContractStatus.ASSIGNED, 0L));
        stats.put("signed", byStatus.getOrDefault(ContractStatus.SIGNED, 0L));
        stats.put("rejected", byStatus.getOrDefault(ContractStatus.REJECTED, 0L));
        stats.put("pendingTasks", taskRepository.countByAssigneeAndTaskStatus(user, TaskStatus.PENDING));
        return stats;
    }

    public Map<String, Object> getMyTaskStatistics(SysUser user) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pendingTasks", taskRepository.countByAssigneeAndTaskStatus(user, TaskStatus.PENDING));
        stats.put("doneTasks", taskRepository.countByAssigneeAndTaskStatus(user, TaskStatus.DONE));
        stats.put("rejectedTasks", taskRepository.countByAssigneeAndTaskStatus(user, TaskStatus.REJECTED));
        return stats;
    }

    @Cacheable(cacheNames = "monthlyStats", key = "'all'")
    public List<Map<String, Object>> getMonthlyStatistics() {
        return contractRepository.countByCreatedMonth().stream()
                .map(row -> {
                    int year = ((Number) row[0]).intValue();
                    int month = ((Number) row[1]).intValue();
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("month", year + "-" + String.format("%02d", month));
                    entry.put("count", ((Number) row[2]).longValue());
                    return entry;
                })
                .collect(Collectors.toCollection(java.util.ArrayList::new));
    }
}
