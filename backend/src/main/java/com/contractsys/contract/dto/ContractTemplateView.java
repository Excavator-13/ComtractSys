package com.contractsys.contract.dto;

import com.contractsys.contract.ContractTemplate;

public record ContractTemplateView(
        Long id,
        String name,
        String description,
        String content,
        String originalName,
        Long fileSize,
        String contentType,
        String visibleRoles,
        boolean enabled
) {
    public static ContractTemplateView from(ContractTemplate template) {
        return new ContractTemplateView(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getContent(),
                template.getOriginalName(),
                template.getFileSize(),
                template.getContentType(),
                template.getVisibleRoles(),
                template.isEnabled()
        );
    }
}
