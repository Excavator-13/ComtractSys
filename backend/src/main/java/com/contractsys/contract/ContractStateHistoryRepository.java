package com.contractsys.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractStateHistoryRepository extends JpaRepository<ContractStateHistory, Long> {
    List<ContractStateHistory> findByContractIdOrderByCreatedAtAsc(Long contractId);
}

