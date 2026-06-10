package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.user.dto.PermissionRequest;
import com.contractsys.user.dto.PermissionView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequirePermission({"permission:manage", "role:manage"})
public class PermissionController {
    private final PermissionService permissionService;
    private final AuthService authService;

    public PermissionController(PermissionService permissionService, AuthService authService) {
        this.permissionService = permissionService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<PermissionView>> list() {
        authService.requireUser();
        return ApiResponse.ok(permissionService.list());
    }

    @PostMapping
    @RequirePermission("permission:manage")
    public ApiResponse<PermissionView> create(@Valid @RequestBody PermissionRequest request) {
        SysUser operator = authService.requireUser();
        return ApiResponse.ok("创建成功", permissionService.create(request, operator));
    }

    @PutMapping("/{id}")
    @RequirePermission("permission:manage")
    public ApiResponse<PermissionView> update(@PathVariable Long id,
                                              @Valid @RequestBody PermissionRequest request) {
        SysUser operator = authService.requireUser();
        return ApiResponse.ok("更新成功", permissionService.update(id, request, operator));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("permission:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser operator = authService.requireUser();
        permissionService.delete(id, operator);
        return ApiResponse.ok(null);
    }
}
