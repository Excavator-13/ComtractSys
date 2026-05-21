package com.contractsys.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record CustomerRequest(
        @NotBlank String name,
        @NotBlank String tel,
        @NotBlank String address,
        String fax,
        String postalCode,
        String bankName,
        String bankAccount,
        String remark
) {
}

