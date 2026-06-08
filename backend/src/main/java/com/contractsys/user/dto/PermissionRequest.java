package com.contractsys.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PermissionRequest(
        @NotBlank @Size(max = 80) String permissionCode,
        @NotBlank @Size(max = 40) String permissionName,
        @NotBlank @Size(max = 40) String module,
        @Size(max = 200) String url,
        @Size(max = 100) String description
) {
}
