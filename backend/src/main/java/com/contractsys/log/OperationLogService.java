package com.contractsys.log;

import com.contractsys.common.PageRequests;
import com.contractsys.common.CsvEscaper;
import com.contractsys.user.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OperationLogService {
    private final OperationLogRepository operationLogRepository;

    public OperationLogService(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    public void record(SysUser operator, String module, String action, String targetType, Long targetId, String content) {
        OperationLog log = new OperationLog();
        log.setOperator(operator);
        log.setOperatorName(operator == null ? "system" : displayName(operator));
        log.setModule(module);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setContent(content);
        operationLogRepository.save(log);
    }

    public Page<OperationLog> list(String keyword, String module, LocalDate startDate, LocalDate endDate, int page, int size) {
        return operationLogRepository.search(
                keyword == null ? "" : keyword,
                module == null ? "" : module,
                startDate == null ? null : startDate.atStartOfDay(),
                exclusiveEnd(endDate),
                PageRequests.of(page, size)
        );
    }

    public byte[] export(String keyword, String module, LocalDate startDate, LocalDate endDate) {
        List<OperationLog> logs = operationLogRepository.search(
                keyword == null ? "" : keyword,
                module == null ? "" : module,
                startDate == null ? null : startDate.atStartOfDay(),
                exclusiveEnd(endDate),
                org.springframework.data.domain.PageRequest.of(0, 10000)
        ).getContent();
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF");
        sb.append("时间,操作人,模块,动作,对象类型,对象ID,内容\n");
        for (OperationLog log : logs) {
            sb.append(log.getCreatedAt()).append(',');
            sb.append(CsvEscaper.escape(log.getOperatorName())).append(',');
            sb.append(CsvEscaper.escape(log.getModule())).append(',');
            sb.append(CsvEscaper.escape(log.getAction())).append(',');
            sb.append(CsvEscaper.escape(log.getTargetType())).append(',');
            sb.append(log.getTargetId() == null ? "" : log.getTargetId()).append(',');
            sb.append(CsvEscaper.escape(log.getContent())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String displayName(SysUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername()
                : user.getDisplayName();
    }

    private LocalDateTime exclusiveEnd(LocalDate endDate) {
        return endDate == null ? null : endDate.plusDays(1).atStartOfDay();
    }

}
