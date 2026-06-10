package com.contractsys.contract;

import com.contractsys.customer.CustomerReferenceChecker;
import org.springframework.stereotype.Component;

@Component
public class ContractCustomerReferenceChecker implements CustomerReferenceChecker {
    private final ContractRepository contractRepository;

    public ContractCustomerReferenceChecker(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @Override
    public boolean hasReference(Long customerId) {
        return contractRepository.existsByCustomerIdAndDeletedFalse(customerId);
    }

    @Override
    public String moduleName() {
        return "合同";
    }
}
