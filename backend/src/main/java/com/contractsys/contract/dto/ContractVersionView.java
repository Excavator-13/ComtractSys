package com.contractsys.contract.dto;

import com.contractsys.contract.ContractVersion;

import java.time.LocalDateTime;

public record ContractVersionView(
        Long id,
        int versionNo,
        String name,
        String content,
        String operatorName,
        String remark,
        LocalDateTime createdAt
) {
    public static ContractVersionView from(ContractVersion version) {
        return new ContractVersionView(
                version.getId(),
                version.getVersionNo(),
                version.getName(),
                version.getContent(),
                version.getOperator().getDisplayName(),
                version.getRemark(),
                version.getCreatedAt()
        );
    }
}
