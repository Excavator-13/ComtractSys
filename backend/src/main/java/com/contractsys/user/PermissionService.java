package com.contractsys.user;

import com.contractsys.common.ApiException;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.user.dto.PermissionRequest;
import com.contractsys.user.dto.PermissionView;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class PermissionService {
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
    private final ApplicationEventPublisher eventPublisher;

    public PermissionService(PermissionRepository permissionRepository,
                             RoleRepository roleRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<PermissionView> list() {
        return permissionRepository.findAll().stream().map(PermissionView::from).toList();
    }

    @Transactional
    public PermissionView create(PermissionRequest request, SysUser operator) {
        if (permissionRepository.existsByPermissionCode(request.permissionCode())) {
            throw ApiException.conflict("权限编码已存在");
        }
        SysPermission permission = new SysPermission();
        permission.setPermissionCode(request.permissionCode());
        applyRequest(permission, request);
        permissionRepository.save(permission);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "SYSTEM", "新增权限", "PERMISSION", permission.getId(),
                permission.getPermissionCode()));
        return PermissionView.from(permission);
    }

    @Transactional
    public PermissionView update(Long id, PermissionRequest request, SysUser operator) {
        SysPermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("权限不存在"));
        if (!permission.getPermissionCode().equals(request.permissionCode())) {
            throw ApiException.conflict("权限编码创建后不可修改");
        }
        applyRequest(permission, request);
        permissionRepository.save(permission);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "SYSTEM", "修改权限", "PERMISSION", permission.getId(),
                permission.getPermissionCode()));
        return PermissionView.from(permission);
    }

    @Transactional
    public void delete(Long id, SysUser operator) {
        SysPermission permission = permissionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("权限不存在"));
        if (CORE_PERMISSION_CODES.contains(permission.getPermissionCode())) {
            throw ApiException.conflict("系统核心权限不能删除");
        }
        if (roleRepository.existsByPermissions_Id(id)) {
            throw ApiException.conflict("该权限已分配给角色，不能删除");
        }
        permissionRepository.delete(permission);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "SYSTEM", "删除权限", "PERMISSION", id,
                permission.getPermissionCode()));
    }

    private void applyRequest(SysPermission permission, PermissionRequest request) {
        permission.setPermissionName(request.permissionName());
        permission.setModule(request.module());
        permission.setUrl(request.url());
        permission.setDescription(request.description());
    }
}
