package com.contractsys.contract.dto;

import com.contractsys.contract.ContractStateHistory;
import com.contractsys.contract.ContractStatus;

import java.time.LocalDateTime;

public record ContractTimelineView(
        Long id,
        ContractStatus fromStatus,
        ContractStatus toStatus,
        String operatorName,
        String remark,
        LocalDateTime createdAt
) {
    public static ContractTimelineView from(ContractStateHistory history) {
        String operatorName = history.getOperator().getDisplayName() == null || history.getOperator().getDisplayName().isBlank()
                ? history.getOperator().getUsername()
                : history.getOperator().getDisplayName();
        return new ContractTimelineView(
                history.getId(),
                history.getFromStatus(),
                history.getToStatus(),
                operatorName,
                history.getRemark(),
                history.getCreatedAt()
        );
    }
}
