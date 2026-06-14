package com.contractsys.user;

import com.contractsys.auth.CurrentUser;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.user.dto.AssignPermissionsRequest;
import com.contractsys.user.dto.RoleOptionView;
import com.contractsys.user.dto.RoleRequest;
import com.contractsys.user.dto.RoleUpdateRequest;
import com.contractsys.user.dto.RoleView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/roles")
@RequirePermission("role:manage")
public class RoleController {
    private static final String CONTRACT_CREATE_PERMISSION = "contract:create";
    private static final Set<String> SYSTEM_ADMIN_PERMISSIONS = Set.of("user:manage", "role:manage", "permission:manage");

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ApiResponse<List<RoleView>> list(@CurrentUser SysUser user) {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/options")
    @RequirePermission("contract:assign")
    public ApiResponse<List<RoleOptionView>> options(@CurrentUser SysUser user) {
        return ApiResponse.ok(roleService.list().stream()
                .filter(RoleController::canCreateContracts)
                .filter(RoleController::isNotSystemAdminRole)
                .map(role -> new RoleOptionView(role.roleCode(), role.roleName()))
                .toList());
    }

    private static boolean canCreateContracts(RoleView role) {
        return role.permissions().stream()
                .anyMatch(permission -> CONTRACT_CREATE_PERMISSION.equals(permission.permissionCode()));
    }

    private static boolean isNotSystemAdminRole(RoleView role) {
        return role.permissions().stream()
                .noneMatch(permission -> SYSTEM_ADMIN_PERMISSIONS.contains(permission.permissionCode()));
    }

    @PostMapping
    public ApiResponse<RoleView> create(@Valid @RequestBody RoleRequest request,
                                        @CurrentUser SysUser operator) {
        return ApiResponse.ok("创建成功", roleService.create(request, operator));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleView> update(@PathVariable Long id,
                                        @Valid @RequestBody RoleUpdateRequest request,
                                        @CurrentUser SysUser operator) {
        return ApiResponse.ok(roleService.update(id, request, operator));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @CurrentUser SysUser operator) {
        roleService.delete(id, operator);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<RoleView> assignPermissions(@PathVariable Long id,
                                                   @Valid @RequestBody AssignPermissionsRequest request,
                                                   @CurrentUser SysUser operator) {
        return ApiResponse.ok(roleService.assignPermissions(id, request, operator));
    }
}
