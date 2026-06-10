package com.contractsys.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractVersionRepository extends JpaRepository<ContractVersion, Long> {
    List<ContractVersion> findByContractIdOrderByVersionNoDesc(Long contractId);
    long countByContractId(Long contractId);
}
