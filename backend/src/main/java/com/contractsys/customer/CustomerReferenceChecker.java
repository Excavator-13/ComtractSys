package com.contractsys.customer;

public interface CustomerReferenceChecker {
    boolean hasReference(Long customerId);

    String moduleName();
}
