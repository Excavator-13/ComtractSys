package com.contractsys.contract;

import com.contractsys.user.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContractTaskRepository extends JpaRepository<ContractTask, Long> {
    List<ContractTask> findByContractIdOrderByCreatedAtAsc(Long contractId);
    List<ContractTask> findByAssigneeAndTaskStatus(SysUser assignee, TaskStatus status);
    @Query("SELECT t FROM ContractTask t WHERE t.assignee = :assignee AND t.taskStatus = com.contractsys.contract.TaskStatus.PENDING " +
           "AND t.round = t.contract.currentRound " +
           "AND ((t.taskType = com.contractsys.contract.TaskType.ASSIGN AND t.contract.status = com.contractsys.contract.ContractStatus.DRAFT) " +
           "OR (t.taskType = com.contractsys.contract.TaskType.COUNTERSIGN AND t.contract.status = com.contractsys.contract.ContractStatus.ASSIGNED) " +
           "OR (t.taskType = com.contractsys.contract.TaskType.FINALIZE AND t.contract.status = com.contractsys.contract.ContractStatus.COUNTERSIGNED) " +
           "OR (t.taskType = com.contractsys.contract.TaskType.APPROVAL AND t.contract.status = com.contractsys.contract.ContractStatus.FINALIZED) " +
           "OR (t.taskType = com.contractsys.contract.TaskType.SIGN AND t.contract.status = com.contractsys.contract.ContractStatus.APPROVED)) " +
           "ORDER BY t.createdAt ASC")
    List<ContractTask> findActivePendingTasksByAssignee(@Param("assignee") SysUser assignee);
    boolean existsByContractIdAndAssigneeId(Long contractId, Long assigneeId);
    long countByTaskStatus(TaskStatus status);
    long countByAssigneeAndTaskStatus(SysUser assignee, TaskStatus status);
    @Query("SELECT COUNT(t) FROM ContractTask t WHERE t.assignee = :assignee AND t.taskStatus = com.contractsys.contract.TaskStatus.PENDING " +
           "AND t.round = t.contract.currentRound " +
           "AND ((t.taskType = com.contractsys.contract.TaskType.ASSIGN AND t.contract.status = com.contractsys.contract.ContractStatus.DRAFT) " +
           "OR (t.taskType = com.contractsys.contract.TaskType.COUNTERSIGN AND t.contract.status = com.contractsys.contract.ContractStatus.ASSIGNED) " +
           "OR (t.taskType = com.contractsys.contract.TaskType.FINALIZE AND t.contract.status = com.contractsys.contract.ContractStatus.COUNTERSIGNED) " +
           "OR (t.taskType = com.contractsys.contract.TaskType.APPROVAL AND t.contract.status = com.contractsys.contract.ContractStatus.FINALIZED) " +
           "OR (t.taskType = com.contractsys.contract.TaskType.SIGN AND t.contract.status = com.contractsys.contract.ContractStatus.APPROVED))")
    long countActivePendingTasksByAssignee(@Param("assignee") SysUser assignee);
    java.util.List<ContractTask> findByContractIdAndTaskType(Long contractId, TaskType taskType);
    java.util.List<ContractTask> findByContractIdAndTaskTypeAndTaskStatus(Long contractId, TaskType taskType, TaskStatus taskStatus);
    java.util.List<ContractTask> findByContractIdAndTaskStatus(Long contractId, TaskStatus taskStatus);
    java.util.List<ContractTask> findByContractIdAndTaskTypeAndRound(Long contractId, TaskType taskType, int round);
    java.util.List<ContractTask> findByContractIdAndRoundAndTaskStatus(Long contractId, int round, TaskStatus taskStatus);
    java.util.Optional<ContractTask> findByContractIdAndAssigneeAndTaskTypeAndTaskStatusAndRound(Long contractId, SysUser assignee, TaskType type, TaskStatus status, int round);
    boolean existsByContractIdAndTaskTypeAndTaskStatusAndRound(Long contractId, TaskType type, TaskStatus status, int round);

    @Query("SELECT t FROM ContractTask t WHERE t.contract.id = :contractId AND t.assignee = :assignee " +
           "AND t.round = :round AND t.taskStatus IN :statuses ORDER BY t.operatedAt DESC")
    java.util.List<ContractTask> findOperatedTasks(@Param("contractId") Long contractId,
                                                   @Param("assignee") SysUser assignee,
                                                   @Param("round") int round,
                                                   @Param("statuses") java.util.Collection<TaskStatus> statuses);
}
