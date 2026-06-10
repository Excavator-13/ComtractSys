package com.contractsys.contract.dto;

import com.contractsys.contract.ContractTemplate;

public record ContractTemplateView(
        Long id,
        String name,
        String description,
        String content
) {
    public static ContractTemplateView from(ContractTemplate template) {
        return new ContractTemplateView(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getContent()
        );
    }
}
