package com.contractsys.contract;

import com.contractsys.common.CsvEscaper;
import com.contractsys.contract.dto.ContractView;
import com.contractsys.user.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Service
public class ContractExportService {
    private final ContractQueryService queryService;
    private final ContractAccessGuard accessGuard;

    public ContractExportService(ContractQueryService queryService,
                                 ContractAccessGuard accessGuard) {
        this.queryService = queryService;
        this.accessGuard = accessGuard;
    }

    public byte[] exportContracts(String keyword, String statusStr, Long customerId, Long drafterId,
                                  LocalDate beginFrom, LocalDate beginTo, LocalDate endFrom, LocalDate endTo,
                                  SysUser user) {
        Page<ContractView> result = accessGuard.hasPermission(user, "contract:query")
                ? queryService.exportAdvancedQuery(keyword, statusStr, customerId, drafterId, beginFrom, beginTo, endFrom, endTo)
                : queryService.exportAdvancedList(keyword, statusStr, customerId, drafterId, beginFrom, beginTo, endFrom, endTo, user);
        StringBuilder sb = new StringBuilder();
        sb.append("\uFEFF");
        sb.append("合同编号,合同名称,客户,状态,起草人,开始日期,结束日期,签订日期\n");
        for (ContractView contract : result.getContent()) {
            sb.append(CsvEscaper.escape(contract.contractNo())).append(',');
            sb.append(CsvEscaper.escape(contract.name())).append(',');
            sb.append(CsvEscaper.escape(contract.customerName())).append(',');
            sb.append(contract.status()).append(',');
            sb.append(CsvEscaper.escape(contract.drafterName())).append(',');
            sb.append(contract.beginDate()).append(',');
            sb.append(contract.endDate()).append(',');
            sb.append(contract.signedDate() == null ? "" : contract.signedDate()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
