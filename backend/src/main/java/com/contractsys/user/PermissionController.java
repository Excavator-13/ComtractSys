package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.user.dto.PermissionView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequirePermission({"permission:manage", "role:manage"})
public class PermissionController {
    private final PermissionRepository permissionRepository;
    private final AuthService authService;

    public PermissionController(PermissionRepository permissionRepository, AuthService authService) {
        this.permissionRepository = permissionRepository;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<PermissionView>> list() {
        authService.requireUser();
        return ApiResponse.ok(permissionRepository.findAll().stream().map(PermissionView::from).toList());
    }
}
