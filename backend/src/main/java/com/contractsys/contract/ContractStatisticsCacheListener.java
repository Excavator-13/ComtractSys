package com.contractsys.contract;

import com.contractsys.common.event.ContractChangedEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ContractStatisticsCacheListener {
    @CacheEvict(cacheNames = {"contractStats", "monthlyStats"}, allEntries = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContractChanged(ContractChangedEvent event) {
        // Cache ownership stays with statistics; workflow code only announces domain changes.
    }
}
