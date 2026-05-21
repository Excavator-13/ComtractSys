package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {
    private final PermissionRepository permissionRepository;
    private final AuthService authService;

    public PermissionController(PermissionRepository permissionRepository, AuthService authService) {
        this.permissionRepository = permissionRepository;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<SysPermission>> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return ApiResponse.ok(permissionRepository.findAll());
    }
}
