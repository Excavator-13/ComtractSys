package com.contractsys.contract.dto;

import java.util.List;

public record ContractDetailView(
        ContractView contract,
        List<TaskView> tasks
) {
}

