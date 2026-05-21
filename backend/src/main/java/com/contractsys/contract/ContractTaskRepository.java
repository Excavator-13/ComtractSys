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
    long countByTaskStatus(TaskStatus status);
    java.util.List<ContractTask> findByContractIdAndTaskType(Long contractId, TaskType taskType);
}

