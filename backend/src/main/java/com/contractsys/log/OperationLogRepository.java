package com.contractsys.log;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    @Query("SELECT l FROM OperationLog l WHERE " +
            "(:keyword = '' OR l.operatorName LIKE %:keyword% OR l.module LIKE %:keyword% OR l.action LIKE %:keyword% OR l.content LIKE %:keyword%) " +
            "AND (:module = '' OR l.module = :module) ORDER BY l.createdAt DESC")
    Page<OperationLog> search(@Param("keyword") String keyword,
                              @Param("module") String module,
                              Pageable pageable);
}
