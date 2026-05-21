package com.contractsys.config;

import com.contractsys.user.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, RoleRepository roleRepository,
                           PermissionRepository permissionRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<SysPermission> permissions = seedPermissions();
        SysRole admin = seedRole("ROLE_ADMIN", "系统管理员", "拥有全部系统权限", permissions);
        seedRole("ROLE_CONTRACT_ADMIN", "合同管理员", "负责合同分配和流程跟踪", permissions.stream()
                .filter(p -> p.getPermissionCode().startsWith("contract:") || p.getPermissionCode().equals("log:view"))
                .toList());
        seedRole("ROLE_OPERATOR", "合同操作员", "负责合同业务操作", permissions.stream()
                .filter(p -> p.getPermissionCode().startsWith("contract:") || p.getPermissionCode().startsWith("customer:"))
                .toList());
        seedRole("ROLE_NEW_USER", "新用户", "注册后的默认角色", List.of());

        if (!userRepository.existsByUsernameAndDeletedFalse("admin")) {
            SysUser user = new SysUser();
            user.setUsername("admin");
            user.setDisplayName("管理员");
            user.setPasswordHash(passwordEncoder.encode("123456"));
            user.getRoles().add(admin);
            userRepository.save(user);
        }
    }

    private List<SysPermission> seedPermissions() {
        return List.of(
                permission("contract:create", "起草合同", "CONTRACT"),
                permission("contract:update", "修改合同", "CONTRACT"),
                permission("contract:delete", "删除合同", "CONTRACT"),
                permission("contract:view", "查看合同", "CONTRACT"),
                permission("contract:assign", "分配合同", "CONTRACT"),
                permission("contract:countersign", "会签合同", "CONTRACT"),
                permission("contract:approve", "审批合同", "CONTRACT"),
                permission("contract:sign", "签订合同", "CONTRACT"),
                permission("customer:manage", "客户管理", "CUSTOMER"),
                permission("user:manage", "用户管理", "SYSTEM"),
                permission("role:manage", "角色管理", "SYSTEM"),
                permission("permission:manage", "权限管理", "SYSTEM"),
                permission("log:view", "日志查询", "LOG")
        );
    }

    private SysPermission permission(String code, String name, String module) {
        return permissionRepository.findAll().stream()
                .filter(p -> p.getPermissionCode().equals(code))
                .findFirst()
                .orElseGet(() -> {
                    SysPermission permission = new SysPermission();
                    permission.setPermissionCode(code);
                    permission.setPermissionName(name);
                    permission.setModule(module);
                    return permissionRepository.save(permission);
                });
    }

    private SysRole seedRole(String code, String name, String description, List<SysPermission> permissions) {
        SysRole role = roleRepository.findByRoleCode(code).orElseGet(SysRole::new);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDescription(description);
        role.getPermissions().clear();
        role.getPermissions().addAll(permissions);
        return roleRepository.save(role);
    }
}

