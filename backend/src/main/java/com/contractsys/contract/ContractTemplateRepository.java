package com.contractsys.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {
    List<ContractTemplate> findByEnabledTrueOrderByCreatedAtAsc();
}
