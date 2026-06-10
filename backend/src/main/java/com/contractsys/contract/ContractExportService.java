package com.contractsys.contract;

import com.contractsys.contract.dto.ContractView;
import com.contractsys.user.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Service
public class ContractExportService {
    private final ContractRepository contractRepository;
    private final ContractQueryService queryService;
    private final ContractAccessGuard accessGuard;

    public ContractExportService(ContractRepository contractRepository,
                                 ContractQueryService queryService,
                                 ContractAccessGuard accessGuard) {
        this.contractRepository = contractRepository;
        this.queryService = queryService;
        this.accessGuard = accessGuard;
    }

    public byte[] exportLogs(String keyword) {
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF");
        sb.append("时间,合同编号,合同名称,操作人,原状态,新状态,备注\n");
        List<ContractStateHistory> list;
        if (keyword == null || keyword.isEmpty()) {
            list = contractRepository.findHistory(org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        } else {
            list = contractRepository.findHistoryByKeyword(keyword, org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        }
        for (ContractStateHistory history : list) {
            sb.append(history.getCreatedAt()).append(',');
            sb.append(escapeCsv(history.getContract().getContractNo())).append(',');
            sb.append(escapeCsv(history.getContract().getName())).append(',');
            sb.append(escapeCsv(history.getOperator().getDisplayName())).append(',');
            sb.append(history.getFromStatus() != null ? history.getFromStatus() : "-").append(',');
            sb.append(history.getToStatus()).append(',');
            sb.append(escapeCsv(history.getRemark() != null ? history.getRemark() : "")).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportContracts(String keyword, String statusStr, Long customerId, Long drafterId,
                                  LocalDate beginFrom, LocalDate beginTo, LocalDate endFrom, LocalDate endTo,
                                  SysUser user) {
        Page<ContractView> result = accessGuard.hasPermission(user, "contract:query")
                ? queryService.advancedQuery(keyword, statusStr, customerId, drafterId, beginFrom, beginTo, endFrom, endTo, 1, 10000)
                : queryService.advancedList(keyword, statusStr, customerId, drafterId, beginFrom, beginTo, endFrom, endTo, 1, 10000, user);
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF");
        sb.append("合同编号,合同名称,客户,状态,起草人,开始日期,结束日期,签订日期\n");
        for (ContractView contract : result.getContent()) {
            sb.append(escapeCsv(contract.contractNo())).append(',');
            sb.append(escapeCsv(contract.name())).append(',');
            sb.append(escapeCsv(contract.customerName())).append(',');
            sb.append(contract.status()).append(',');
            sb.append(escapeCsv(contract.drafterName())).append(',');
            sb.append(contract.beginDate()).append(',');
            sb.append(contract.endDate()).append(',');
            sb.append(contract.signedDate() == null ? "" : contract.signedDate()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
