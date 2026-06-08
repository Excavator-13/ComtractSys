package com.contractsys.user;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiException;
import com.contractsys.common.ApiResponse;
import com.contractsys.log.OperationLogService;
import com.contractsys.user.dto.PermissionRequest;
import com.contractsys.user.dto.PermissionView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/permissions")
@RequirePermission({"permission:manage", "role:manage"})
public class PermissionController {
    private static final Set<String> CORE_PERMISSION_CODES = Set.of(
            "contract:create",
            "contract:update",
            "contract:delete",
            "contract:view",
            "contract:query",
            "contract:assign",
            "contract:countersign",
            "contract:approve",
            "contract:sign",
            "customer:manage",
            "user:manage",
            "role:manage",
            "permission:manage",
            "log:view"
    );

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final AuthService authService;
    private final OperationLogService operationLogService;

    public PermissionController(PermissionRepository permissionRepository, RoleRepository roleRepository,
                                AuthService authService, OperationLogService operationLogService) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.authService = authService;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<List<PermissionView>> list() {
        authService.requireUser();
        return ApiResponse.ok(permissionRepository.findAll().stream().map(PermissionView::from).toList());
    }

    @PostMapping
    @RequirePermission("permission:manage")
    public ApiResponse<PermissionView> create(@Valid @RequestBody PermissionRequest request) {
        SysUser operator = authService.requireUser();
        if (permissionRepository.existsByPermissionCode(request.permissionCode())) {
            throw ApiException.conflict("权限编码已存在");
        }
        SysPermission permission = new SysPermission();
        permission.setPermissionCode(request.permissionCode());
        applyRequest(permission, request);
        permissionRepository.save(permission);
        operationLogService.record(operator, "SYSTEM", "新增权限", "PERMISSION", permission.getId(),
                permission.getPermissionCode());
        return ApiResponse.ok("创建成功", PermissionView.from(permission));
    }

    @PutMapping("/{id}")
    @RequirePermission("permission:manage")
    public ApiResponse<PermissionView> update(@PathVariable Long id,
                                              @Valid @RequestBody PermissionRequest request) {
        SysUser operator = authService.requireUser();
        SysPermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("权限不存在"));
        if (!permission.getPermissionCode().equals(request.permissionCode())) {
            throw ApiException.conflict("权限编码创建后不可修改");
        }
        applyRequest(permission, request);
        permissionRepository.save(permission);
        operationLogService.record(operator, "SYSTEM", "修改权限", "PERMISSION", permission.getId(),
                permission.getPermissionCode());
        return ApiResponse.ok("更新成功", PermissionView.from(permission));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("permission:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser operator = authService.requireUser();
        SysPermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("权限不存在"));
        if (CORE_PERMISSION_CODES.contains(permission.getPermissionCode())) {
            throw ApiException.conflict("系统核心权限不能删除");
        }
        if (roleRepository.existsByPermissions_Id(id)) {
            throw ApiException.conflict("该权限已分配给角色，不能删除");
        }
        permissionRepository.delete(permission);
        operationLogService.record(operator, "SYSTEM", "删除权限", "PERMISSION", id,
                permission.getPermissionCode());
        return ApiResponse.ok(null);
    }

    private void applyRequest(SysPermission permission, PermissionRequest request) {
        permission.setPermissionName(request.permissionName());
        permission.setModule(request.module());
        permission.setUrl(request.url());
        permission.setDescription(request.description());
    }
}
