package com.contractsys.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<SysPermission, Long> {
    boolean existsByPermissionCode(String permissionCode);
}

