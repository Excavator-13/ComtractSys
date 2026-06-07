package com.contractsys.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsernameAndDeletedFalse(String username);
    boolean existsByUsernameAndDeletedFalse(String username);
    Page<SysUser> findByDeletedFalse(Pageable pageable);
    Page<SysUser> findByDeletedFalseAndUsernameContainingIgnoreCase(String username, Pageable pageable);
    boolean existsByRoles_Id(Long roleId);

    @Query("SELECT DISTINCT u FROM SysUser u JOIN u.roles r JOIN r.permissions p " +
           "WHERE u.deleted=false AND u.status=com.contractsys.user.UserStatus.ENABLED " +
           "AND p.permissionCode = :permission")
    List<SysUser> findEnabledUsersWithPermission(@Param("permission") String permission);
}
