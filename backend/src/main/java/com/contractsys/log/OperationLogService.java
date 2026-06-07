package com.contractsys.log;

import com.contractsys.common.PageRequests;
import com.contractsys.user.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
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

    public Page<OperationLog> list(String keyword, String module, int page, int size) {
        return operationLogRepository.search(
                keyword == null ? "" : keyword,
                module == null ? "" : module,
                PageRequests.of(page, size)
        );
    }

    public byte[] export(String keyword, String module) {
        List<OperationLog> logs = operationLogRepository.search(
                keyword == null ? "" : keyword,
                module == null ? "" : module,
                org.springframework.data.domain.PageRequest.of(0, 10000)
        ).getContent();
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF");
        sb.append("时间,操作人,模块,动作,对象类型,对象ID,内容\n");
        for (OperationLog log : logs) {
            sb.append(log.getCreatedAt()).append(',');
            sb.append(escapeCsv(log.getOperatorName())).append(',');
            sb.append(escapeCsv(log.getModule())).append(',');
            sb.append(escapeCsv(log.getAction())).append(',');
            sb.append(escapeCsv(log.getTargetType())).append(',');
            sb.append(log.getTargetId() == null ? "" : log.getTargetId()).append(',');
            sb.append(escapeCsv(log.getContent())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String displayName(SysUser user) {
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername()
                : user.getDisplayName();
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}
