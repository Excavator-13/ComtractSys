package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiException;
import com.contractsys.common.ApiResponse;
import com.contractsys.user.dto.AssignPermissionsRequest;
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
    private static final Set<String> BUILT_IN_ROLES = Set.of("ROLE_ADMIN", "ROLE_CONTRACT_ADMIN", "ROLE_OPERATOR", "ROLE_NEW_USER");
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuthService authService;

    public RoleController(RoleRepository roleRepository, PermissionRepository permissionRepository,
                          AuthService authService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<RoleView>> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return ApiResponse.ok(roleRepository.findAll().stream().map(RoleView::from).toList());
    }

    @PostMapping
    public ApiResponse<RoleView> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @Valid @RequestBody RoleRequest request) {
        authService.requireUser(authorization);
        if (roleRepository.existsByRoleCode(request.roleCode())) {
            throw ApiException.conflict("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setDescription(request.description());
        return ApiResponse.ok("创建成功", RoleView.from(roleRepository.save(role)));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleView> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                        @PathVariable Long id,
                                        @Valid @RequestBody RoleUpdateRequest request) {
        authService.requireUser(authorization);
        SysRole role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        if (BUILT_IN_ROLES.contains(role.getRoleCode()) && !role.getRoleName().equals(request.roleName())) {
            throw ApiException.conflict("系统内置角色名称不允许修改");
        }
        role.setRoleName(request.roleName());
        role.setDescription(request.description());
        return ApiResponse.ok(RoleView.from(roleRepository.save(role)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     @PathVariable Long id) {
        authService.requireUser(authorization);
        SysRole role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        if (BUILT_IN_ROLES.contains(role.getRoleCode())) {
            throw ApiException.conflict("系统内置角色不能删除");
        }
        roleRepository.delete(role);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<RoleView> assignPermissions(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody AssignPermissionsRequest request) {
        authService.requireUser(authorization);
        SysRole role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        if ("ROLE_ADMIN".equals(role.getRoleCode()) && (request.permissionIds() == null || request.permissionIds().isEmpty())) {
            throw ApiException.conflict("系统管理员角色不能清空权限");
        }
        role.getPermissions().clear();
        if (request.permissionIds() != null) {
            request.permissionIds().forEach(permissionId -> permissionRepository.findById(permissionId).ifPresent(role.getPermissions()::add));
        }
        return ApiResponse.ok(RoleView.from(roleRepository.save(role)));
    }
}
