package com.contractsys.user;

import com.contractsys.auth.CurrentUser;
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

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    public ApiResponse<List<PermissionView>> list(@CurrentUser SysUser user) {
        return ApiResponse.ok(permissionService.list());
    }

    @PostMapping
    @RequirePermission("permission:manage")
    public ApiResponse<PermissionView> create(@Valid @RequestBody PermissionRequest request,
                                              @CurrentUser SysUser operator) {
        return ApiResponse.ok("创建成功", permissionService.create(request, operator));
    }

    @PutMapping("/{id}")
    @RequirePermission("permission:manage")
    public ApiResponse<PermissionView> update(@PathVariable Long id,
                                              @Valid @RequestBody PermissionRequest request,
                                              @CurrentUser SysUser operator) {
        return ApiResponse.ok("更新成功", permissionService.update(id, request, operator));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("permission:manage")
    public ApiResponse<Void> delete(@PathVariable Long id, @CurrentUser SysUser operator) {
        permissionService.delete(id, operator);
        return ApiResponse.ok(null);
    }
}
