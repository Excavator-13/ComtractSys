package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiException;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.user.dto.AssignPermissionsRequest;
import com.contractsys.user.dto.RoleRequest;
import com.contractsys.user.dto.RoleUpdateRequest;
import com.contractsys.user.dto.RoleView;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
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
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;

    public RoleController(RoleRepository roleRepository, PermissionRepository permissionRepository,
                          UserRepository userRepository, AuthService authService,
                          ApplicationEventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    public ApiResponse<List<RoleView>> list() {
        authService.requireUser();
        return ApiResponse.ok(roleRepository.findAll().stream().map(RoleView::from).toList());
    }

    @PostMapping
    public ApiResponse<RoleView> create(@Valid @RequestBody RoleRequest request) {
        SysUser operator = authService.requireUser();
        if (roleRepository.existsByRoleCode(request.roleCode())) {
            throw ApiException.conflict("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setDescription(request.description());
        SysRole saved = roleRepository.save(role);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "ROLE", "新增角色", "ROLE", saved.getId(), saved.getRoleCode()));
        return ApiResponse.ok("创建成功", RoleView.from(saved));
    }

    @PutMapping("/{id}")
    public ApiResponse<RoleView> update(@PathVariable Long id,
                                        @Valid @RequestBody RoleUpdateRequest request) {
        SysUser operator = authService.requireUser();
        SysRole role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        if (BUILT_IN_ROLES.contains(role.getRoleCode()) && !role.getRoleName().equals(request.roleName())) {
            throw ApiException.conflict("系统内置角色名称不允许修改");
        }
        role.setRoleName(request.roleName());
        role.setDescription(request.description());
        SysRole saved = roleRepository.save(role);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "ROLE", "修改角色", "ROLE", saved.getId(), saved.getRoleCode()));
        return ApiResponse.ok(RoleView.from(saved));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser operator = authService.requireUser();
        SysRole role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        if (BUILT_IN_ROLES.contains(role.getRoleCode())) {
            throw ApiException.conflict("系统内置角色不能删除");
        }
        if (userRepository.existsByRoles_Id(role.getId())) {
            throw ApiException.conflict("角色已被用户绑定，不能删除");
        }
        roleRepository.delete(role);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "ROLE", "删除角色", "ROLE", role.getId(), role.getRoleCode()));
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/permissions")
    public ApiResponse<RoleView> assignPermissions(@PathVariable Long id,
                                                   @Valid @RequestBody AssignPermissionsRequest request) {
        SysUser operator = authService.requireUser();
        SysRole role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("角色不存在"));
        if ("ROLE_ADMIN".equals(role.getRoleCode()) && (request.permissionIds() == null || request.permissionIds().isEmpty())) {
            throw ApiException.conflict("系统管理员角色不能清空权限");
        }
        role.getPermissions().clear();
        if (request.permissionIds() != null) {
            request.permissionIds().forEach(permissionId -> role.getPermissions().add(
                    permissionRepository.findById(permissionId)
                            .orElseThrow(() -> ApiException.notFound("权限不存在: " + permissionId))
            ));
        }
        SysRole saved = roleRepository.save(role);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "ROLE", "分配权限", "ROLE", saved.getId(), saved.getRoleCode()));
        return ApiResponse.ok(RoleView.from(saved));
    }
}
