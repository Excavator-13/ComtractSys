package com.contractsys.contract.dto;

import com.contractsys.contract.Contract;
import com.contractsys.contract.ContractStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContractView(
        Long id,
        String contractNo,
        String name,
        Long customerId,
        String customerName,
        LocalDate beginDate,
        LocalDate endDate,
        String content,
        Long drafterId,
        String drafterName,
        ContractStatus status,
        LocalDate signedDate,
        String signInfo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ContractView from(Contract contract) {
        return new ContractView(
                contract.getId(),
                contract.getContractNo(),
                contract.getName(),
                contract.getCustomer().getId(),
                contract.getCustomer().getName(),
                contract.getBeginDate(),
                contract.getEndDate(),
                contract.getContent(),
                contract.getDrafter().getId(),
                contract.getDrafter().getDisplayName(),
                contract.getStatus(),
                contract.getSignedDate(),
                contract.getSignInfo(),
                contract.getCreatedAt(),
                contract.getUpdatedAt()
        );
    }
}

