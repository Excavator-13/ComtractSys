package com.contractsys.user;

import com.contractsys.common.ApiException;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.user.dto.AssignPermissionsRequest;
import com.contractsys.user.dto.RoleRequest;
import com.contractsys.user.dto.RoleUpdateRequest;
import com.contractsys.user.dto.RoleView;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class RoleService {
    private static final Set<String> BUILT_IN_ROLES = Set.of("ROLE_ADMIN", "ROLE_CONTRACT_ADMIN", "ROLE_OPERATOR", "ROLE_NEW_USER");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RoleService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       UserRepository userRepository,
                       ApplicationEventPublisher eventPublisher) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<RoleView> list() {
        return roleRepository.findAll().stream().map(RoleView::from).toList();
    }

    @Transactional
    public RoleView create(RoleRequest request, SysUser operator) {
        if (roleRepository.existsByRoleCode(request.roleCode())) {
            throw ApiException.conflict("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setDescription(request.description());
        SysRole saved = roleRepository.save(role);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "ROLE", "新增角色", "ROLE", saved.getId(), saved.getRoleCode()));
        return RoleView.from(saved);
    }

    @Transactional
    public RoleView update(Long id, RoleUpdateRequest request, SysUser operator) {
        SysRole role = findRole(id);
        if (BUILT_IN_ROLES.contains(role.getRoleCode()) && !role.getRoleName().equals(request.roleName())) {
            throw ApiException.conflict("系统内置角色名称不允许修改");
        }
        role.setRoleName(request.roleName());
        role.setDescription(request.description());
        SysRole saved = roleRepository.save(role);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "ROLE", "修改角色", "ROLE", saved.getId(), saved.getRoleCode()));
        return RoleView.from(saved);
    }

    @Transactional
    public void delete(Long id, SysUser operator) {
        SysRole role = findRole(id);
        if (BUILT_IN_ROLES.contains(role.getRoleCode())) {
            throw ApiException.conflict("系统内置角色不能删除");
        }
        if (userRepository.existsByRoles_Id(role.getId())) {
            throw ApiException.conflict("角色已被用户绑定，不能删除");
        }
        roleRepository.delete(role);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "ROLE", "删除角色", "ROLE", role.getId(), role.getRoleCode()));
    }

    @Transactional
    public RoleView assignPermissions(Long id, AssignPermissionsRequest request, SysUser operator) {
        SysRole role = findRole(id);
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
        return RoleView.from(saved);
    }

    private SysRole findRole(Long roleId) {
        return roleRepository.findById(roleId).orElseThrow(() -> ApiException.notFound("角色不存在"));
    }
}
