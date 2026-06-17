package com.contractsys.contract.dto;

import com.contractsys.contract.ContractTask;
import com.contractsys.contract.ContractStatus;
import com.contractsys.contract.TaskStatus;
import com.contractsys.contract.TaskType;

import java.time.LocalDateTime;

public record TaskView(
        Long id,
        Long contractId,
        String contractName,
        TaskType taskType,
        TaskStatus taskStatus,
        Long assigneeId,
        String assigneeName,
        String opinion,
        int round,
        ContractStatus contractStatus,
        String contractReturnTargetStage,
        int contractCurrentRound,
        LocalDateTime operatedAt
) {
    public static TaskView from(ContractTask task) {
        return new TaskView(
                task.getId(),
                task.getContract().getId(),
                task.getContract().getName(),
                task.getTaskType(),
                task.getTaskStatus(),
                task.getAssignee().getId(),
                task.getAssignee().getDisplayName(),
                task.getOpinion(),
                task.getRound(),
                task.getContract().getStatus(),
                task.getContract().getReturnTargetStage(),
                task.getContract().getCurrentRound(),
                task.getOperatedAt()
        );
    }
}
