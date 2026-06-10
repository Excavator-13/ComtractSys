package com.contractsys.log;

import com.contractsys.auth.CurrentUser;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import com.contractsys.user.SysUser;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/logs")
@RequirePermission("log:view")
public class OperationLogController {
    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public ApiResponse<PageResponse<OperationLog>> logs(@RequestParam(defaultValue = "") String keyword,
                                                        @RequestParam(defaultValue = "") String module,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size,
                                                        @CurrentUser SysUser user) {
        return ApiResponse.ok(PageResponse.from(operationLogService.list(keyword, module, page, size)));
    }

    @GetMapping("/export")
    public ResponseEntity<Resource> exportLogs(@RequestParam(defaultValue = "") String keyword,
                                               @RequestParam(defaultValue = "") String module,
                                               @CurrentUser SysUser user) {
        byte[] csv = operationLogService.export(keyword, module);
        Resource resource = new ByteArrayResource(csv);
        String filename = "logs_" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(resource);
    }
}
