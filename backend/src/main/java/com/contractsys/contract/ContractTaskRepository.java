package com.contractsys.contract;

import com.contractsys.user.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractTaskRepository extends JpaRepository<ContractTask, Long> {
    List<ContractTask> findByContractIdOrderByCreatedAtAsc(Long contractId);
    List<ContractTask> findByAssigneeAndTaskStatus(SysUser assignee, TaskStatus status);
    Optional<ContractTask> findByContractIdAndAssigneeAndTaskTypeAndTaskStatus(Long contractId, SysUser assignee, TaskType type, TaskStatus status);
    boolean existsByContractIdAndTaskTypeAndTaskStatus(Long contractId, TaskType type, TaskStatus status);
    boolean existsByContractIdAndAssigneeId(Long contractId, Long assigneeId);
    long countByTaskStatus(TaskStatus status);
    long countByAssigneeAndTaskStatus(SysUser assignee, TaskStatus status);
    java.util.List<ContractTask> findByContractIdAndTaskType(Long contractId, TaskType taskType);
    java.util.List<ContractTask> findByContractIdAndTaskTypeAndTaskStatus(Long contractId, TaskType taskType, TaskStatus taskStatus);
    java.util.List<ContractTask> findByContractIdAndTaskStatus(Long contractId, TaskStatus taskStatus);
}
