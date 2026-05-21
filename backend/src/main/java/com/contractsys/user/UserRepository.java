package com.contractsys.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsernameAndDeletedFalse(String username);
    boolean existsByUsernameAndDeletedFalse(String username);
    Page<SysUser> findByDeletedFalse(Pageable pageable);
    Page<SysUser> findByDeletedFalseAndUsernameContainingIgnoreCase(String username, Pageable pageable);
}

