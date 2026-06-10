package com.contractsys.contract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    @Query("SELECT c FROM Contract c WHERE c.deleted=false " +
           "AND (c.name LIKE %:keyword% OR c.contractNo LIKE %:keyword% OR c.customer.name LIKE %:keyword%) " +
           "AND (:status IS NULL OR c.status = :status) ORDER BY c.createdAt DESC")
    Page<Contract> search(@Param("keyword") String keyword, @Param("status") ContractStatus status, Pageable pageable);

    @Query("SELECT c FROM Contract c WHERE c.deleted=false " +
           "AND (c.name LIKE %:keyword% OR c.contractNo LIKE %:keyword% OR c.customer.name LIKE %:keyword% OR c.drafter.username LIKE %:keyword%) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:customerId IS NULL OR c.customer.id = :customerId) " +
           "AND (:drafterId IS NULL OR c.drafter.id = :drafterId) " +
           "AND (:beginFrom IS NULL OR c.beginDate >= :beginFrom) " +
           "AND (:beginTo IS NULL OR c.beginDate <= :beginTo) " +
           "AND (:endFrom IS NULL OR c.endDate >= :endFrom) " +
           "AND (:endTo IS NULL OR c.endDate <= :endTo) " +
           "ORDER BY c.createdAt DESC")
    Page<Contract> advancedSearch(@Param("keyword") String keyword,
                                  @Param("status") ContractStatus status,
                                  @Param("customerId") Long customerId,
                                  @Param("drafterId") Long drafterId,
                                  @Param("beginFrom") LocalDate beginFrom,
                                  @Param("beginTo") LocalDate beginTo,
                                  @Param("endFrom") LocalDate endFrom,
                                  @Param("endTo") LocalDate endTo,
                                  Pageable pageable);

    @Query("SELECT DISTINCT c FROM Contract c LEFT JOIN ContractTask t ON t.contract = c WHERE c.deleted=false " +
           "AND (c.name LIKE %:keyword% OR c.contractNo LIKE %:keyword% OR c.customer.name LIKE %:keyword%) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (c.drafter.id = :userId OR t.assignee.id = :userId OR (:includeDraftForAssigner = true AND c.status = :draftStatus)) " +
           "ORDER BY c.createdAt DESC")
    Page<Contract> searchRelated(@Param("keyword") String keyword,
                                  @Param("status") ContractStatus status,
                                  @Param("userId") Long userId,
                                  @Param("includeDraftForAssigner") boolean includeDraftForAssigner,
                                  @Param("draftStatus") ContractStatus draftStatus,
                                  Pageable pageable);

    @Query("SELECT DISTINCT c FROM Contract c LEFT JOIN ContractTask t ON t.contract = c WHERE c.deleted=false " +
           "AND (c.name LIKE %:keyword% OR c.contractNo LIKE %:keyword% OR c.customer.name LIKE %:keyword% OR c.drafter.username LIKE %:keyword%) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:customerId IS NULL OR c.customer.id = :customerId) " +
           "AND (:drafterId IS NULL OR c.drafter.id = :drafterId) " +
           "AND (:beginFrom IS NULL OR c.beginDate >= :beginFrom) " +
           "AND (:beginTo IS NULL OR c.beginDate <= :beginTo) " +
           "AND (:endFrom IS NULL OR c.endDate >= :endFrom) " +
           "AND (:endTo IS NULL OR c.endDate <= :endTo) " +
           "AND (c.drafter.id = :userId OR t.assignee.id = :userId OR (:includeDraftForAssigner = true AND c.status = :draftStatus)) " +
           "ORDER BY c.createdAt DESC")
    Page<Contract> advancedSearchRelated(@Param("keyword") String keyword,
                                          @Param("status") ContractStatus status,
                                          @Param("customerId") Long customerId,
                                          @Param("drafterId") Long drafterId,
                                          @Param("beginFrom") LocalDate beginFrom,
                                          @Param("beginTo") LocalDate beginTo,
                                          @Param("endFrom") LocalDate endFrom,
                                          @Param("endTo") LocalDate endTo,
                                          @Param("userId") Long userId,
                                          @Param("includeDraftForAssigner") boolean includeDraftForAssigner,
                                          @Param("draftStatus") ContractStatus draftStatus,
                                          Pageable pageable);

    @Query("SELECT h FROM ContractStateHistory h WHERE h.contract.deleted=false " +
           "ORDER BY h.createdAt DESC")
    Page<ContractStateHistory> findHistory(Pageable pageable);

    @Query("SELECT h FROM ContractStateHistory h WHERE h.contract.deleted=false " +
           "AND (h.contract.name LIKE %:keyword% OR h.contract.contractNo LIKE %:keyword% OR h.operator.username LIKE %:keyword%) " +
           "ORDER BY h.createdAt DESC")
    Page<ContractStateHistory> findHistoryByKeyword(@Param("keyword") String keyword, Pageable pageable);

    long countByDeletedFalse();
    long countByDeletedFalseAndStatus(ContractStatus status);
    boolean existsByCustomerIdAndDeletedFalse(Long customerId);

    @Query("SELECT c.status, COUNT(c) FROM Contract c WHERE c.deleted=false GROUP BY c.status")
    List<Object[]> countByStatus();

    @Query("SELECT year(c.createdAt), month(c.createdAt), COUNT(c) " +
           "FROM Contract c WHERE c.deleted=false GROUP BY year(c.createdAt), month(c.createdAt) " +
           "ORDER BY year(c.createdAt), month(c.createdAt)")
    List<Object[]> countByCreatedMonth();
}
