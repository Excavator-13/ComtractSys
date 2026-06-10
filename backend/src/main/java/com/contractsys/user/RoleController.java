package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.user.dto.AssignPermissionsRequest;
import com.contractsys.user.dto.RoleRequest;
import com.contractsys.user.dto.RoleUpdateRequest;
import com.contractsys.user.dto.RoleView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequirePermission("role:manage")
public class RoleController {
    private final RoleService roleService;
    private final AuthService authService;

    public RoleController(RoleService roleService, AuthService authService) {
        this.roleService = roleService;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<RoleView>> list() {
        authService.requireUser();
        return ApiResponse.ok(roleService.list());
    }

    @PostMapping
    public ApiResponse<RoleView> create(@Valid @RequestBody RoleRequest request) {
        SysUser operator = authService.requireUser();
        return ApiResponse.ok("创建成功", roleService.create(request, operator));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleView> update(@PathVariable Long id,
                                        @Valid @RequestBody RoleUpdateRequest request) {
        SysUser operator = authService.requireUser();
        return ApiResponse.ok(roleService.update(id, request, operator));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser operator = authService.requireUser();
        roleService.delete(id, operator);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<RoleView> assignPermissions(@PathVariable Long id,
                                                   @Valid @RequestBody AssignPermissionsRequest request) {
        SysUser operator = authService.requireUser();
        return ApiResponse.ok(roleService.assignPermissions(id, request, operator));
    }
}
