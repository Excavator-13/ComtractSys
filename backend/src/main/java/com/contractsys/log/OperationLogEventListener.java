package com.contractsys.log;

import com.contractsys.common.event.OperationLogEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OperationLogEventListener {
    private final OperationLogService operationLogService;

    public OperationLogEventListener(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void onOperationLog(OperationLogEvent event) {
        operationLogService.record(
                event.operator(),
                event.module(),
                event.action(),
                event.targetType(),
                event.targetId(),
                event.content()
        );
    }
}
