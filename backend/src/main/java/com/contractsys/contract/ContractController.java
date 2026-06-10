package com.contractsys.contract;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import com.contractsys.contract.dto.*;
import com.contractsys.user.SysUser;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ContractController {
    private final ContractService contractService;
    private final ContractQueryService queryService;
    private final ContractExportService exportService;
    private final ContractStatisticsService statisticsService;
    private final ContractAttachmentService attachmentService;
    private final AuthService authService;

    public ContractController(ContractService contractService,
                              ContractQueryService queryService,
                              ContractExportService exportService,
                              ContractStatisticsService statisticsService,
                              ContractAttachmentService attachmentService,
                              AuthService authService) {
        this.contractService = contractService;
        this.queryService = queryService;
        this.exportService = exportService;
        this.statisticsService = statisticsService;
        this.attachmentService = attachmentService;
        this.authService = authService;
    }

    @GetMapping("/contracts")
    @RequirePermission("contract:view")
    public ApiResponse<PageResponse<ContractView>> list(@RequestParam(defaultValue = "") String keyword,
                                                        @RequestParam(defaultValue = "") String status,
                                                        @RequestParam(required = false) Long customerId,
                                                        @RequestParam(required = false) Long drafterId,
                                                        @RequestParam(required = false) LocalDate beginFrom,
                                                        @RequestParam(required = false) LocalDate beginTo,
                                                        @RequestParam(required = false) LocalDate endFrom,
                                                        @RequestParam(required = false) LocalDate endTo,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(PageResponse.from(queryService.advancedList(
                keyword, status, customerId, drafterId, beginFrom, beginTo, endFrom, endTo, page, size, user)));
    }

    @GetMapping("/contracts/query")
    @RequirePermission("contract:query")
    public ApiResponse<PageResponse<ContractView>> query(@RequestParam(defaultValue = "") String keyword,
                                                         @RequestParam(defaultValue = "") String status,
                                                         @RequestParam(required = false) Long customerId,
                                                         @RequestParam(required = false) Long drafterId,
                                                         @RequestParam(required = false) LocalDate beginFrom,
                                                         @RequestParam(required = false) LocalDate beginTo,
                                                         @RequestParam(required = false) LocalDate endFrom,
                                                         @RequestParam(required = false) LocalDate endTo,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        authService.requireUser();
        return ApiResponse.ok(PageResponse.from(queryService.advancedQuery(
                keyword, status, customerId, drafterId, beginFrom, beginTo, endFrom, endTo, page, size)));
    }

    @GetMapping("/contracts/export")
    @RequirePermission({"contract:view", "contract:query"})
    public ResponseEntity<Resource> exportContracts(@RequestParam(defaultValue = "") String keyword,
                                                    @RequestParam(defaultValue = "") String status,
                                                    @RequestParam(required = false) Long customerId,
                                                    @RequestParam(required = false) Long drafterId,
                                                    @RequestParam(required = false) LocalDate beginFrom,
                                                    @RequestParam(required = false) LocalDate beginTo,
                                                    @RequestParam(required = false) LocalDate endFrom,
                                                    @RequestParam(required = false) LocalDate endTo) {
        SysUser user = authService.requireUser();
        byte[] csv = exportService.exportContracts(keyword, status, customerId, drafterId, beginFrom, beginTo, endFrom, endTo, user);
        Resource resource = new org.springframework.core.io.ByteArrayResource(csv);
        String filename = "contracts_" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(resource);
    }

    @GetMapping("/contract-templates")
    @RequirePermission("contract:create")
    public ApiResponse<List<ContractTemplateView>> templates() {
        authService.requireUser();
        return ApiResponse.ok(queryService.templates());
    }

    @GetMapping("/contracts/{id}")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<ContractDetailView> detail(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(queryService.detail(id, user));
    }

    @PostMapping(value = "/contracts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @RequirePermission("contract:create")
    public ApiResponse<ContractView> create(@Valid @RequestBody ContractCreateRequest request) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok("起草成功", contractService.create(request, user));
    }

    @PostMapping(value = "/contracts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("contract:create")
    public ApiResponse<ContractView> createMultipart(@RequestParam String name,
                                                     @RequestParam Long customerId,
                                                     @RequestParam LocalDate beginDate,
                                                     @RequestParam LocalDate endDate,
                                                     @RequestParam String content,
                                                     @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        SysUser user = authService.requireUser();
        ContractView created = contractService.create(new ContractCreateRequest(name, customerId, beginDate, endDate, content), user);
        attachmentService.uploadAll(created.id(), files, user);
        return ApiResponse.ok("起草成功", created);
    }

    @PostMapping("/contracts/{id}/assign")
    @RequirePermission("contract:assign")
    public ApiResponse<ContractDetailView> assign(@PathVariable Long id,
                                                  @Valid @RequestBody AssignRequest request) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.assign(id, request, user));
    }

    @GetMapping("/tasks/my")
    @RequirePermission({"contract:assign", "contract:countersign", "contract:approve", "contract:sign", "contract:update"})
    public ApiResponse<List<TaskView>> myTasks() {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(queryService.myTasks(user));
    }

    @GetMapping("/contracts/{id}/process")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<Map<String, Object>> process(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(queryService.process(id, user));
    }

    @GetMapping("/contracts/{id}/versions")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<List<ContractVersionView>> versions(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(queryService.versions(id, user));
    }

    @GetMapping("/contracts/{id}/timeline")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<List<ContractTimelineView>> timeline(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(queryService.timeline(id, user));
    }

    @PostMapping("/contracts/{id}/countersign")
    @RequirePermission("contract:countersign")
    public ApiResponse<ContractDetailView> countersign(@PathVariable Long id,
                                                       @Valid @RequestBody OpinionRequest request) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.countersign(id, request, user));
    }

    @PostMapping("/contracts/{id}/finalize")
    @RequirePermission("contract:update")
    public ApiResponse<ContractDetailView> finalizeContract(@PathVariable Long id,
                                                           @Valid @RequestBody FinalizeRequest request) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.finalizeContract(id, request, user));
    }

    @PostMapping("/contracts/{id}/approve")
    @RequirePermission("contract:approve")
    public ApiResponse<ContractDetailView> approve(@PathVariable Long id,
                                                   @Valid @RequestBody ApproveRequest request) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.approve(id, request, user));
    }

    @PostMapping("/contracts/{id}/sign")
    @RequirePermission("contract:sign")
    public ApiResponse<ContractDetailView> sign(@PathVariable Long id,
                                                @Valid @RequestBody SignRequest request) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.sign(id, request, user));
    }

    @PutMapping("/contracts/{id}")
    @RequirePermission("contract:update")
    public ApiResponse<ContractView> update(@PathVariable Long id,
                                            @Valid @RequestBody ContractCreateRequest request) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.update(id, request, user));
    }

    @DeleteMapping("/contracts/{id}")
    @RequirePermission("contract:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        contractService.delete(id, user);
        return ApiResponse.ok(null);
    }

    @PostMapping("/contracts/{id}/cancel")
    @RequirePermission("contract:delete")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        contractService.cancel(id, user);
        return ApiResponse.ok(null);
    }

    @PostMapping("/contracts/{id}/attachments")
    @RequirePermission("contract:update")
    public ApiResponse<AttachmentView> uploadAttachment(@PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok("上传成功", attachmentService.upload(id, file, user));
    }

    @GetMapping("/contracts/{id}/attachments")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<List<AttachmentView>> listAttachments(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(attachmentService.list(id, user));
    }

    @GetMapping("/attachments/{id}/download")
    @RequirePermission({"contract:view", "contract:query"})
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        ContractAttachmentService.AttachmentResource attachment = attachmentService.download(id, user);
        String encodedName = URLEncoder.encode(attachment.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(attachment.contentType())
                .body(attachment.resource());
    }

    @GetMapping("/attachments/{id}/preview")
    @RequirePermission({"contract:view", "contract:query"})
    public ResponseEntity<Resource> previewAttachment(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        ContractAttachmentService.AttachmentResource attachment = attachmentService.preview(id, user);
        String encodedName = URLEncoder.encode(attachment.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedName)
                .contentType(attachment.contentType())
                .body(attachment.resource());
    }

    @DeleteMapping("/attachments/{id}")
    @RequirePermission("contract:update")
    public ApiResponse<Void> deleteAttachment(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        attachmentService.delete(id, user);
        return ApiResponse.ok(null);
    }

    @PostMapping("/contracts/{id}/resubmit")
    @RequirePermission("contract:update")
    public ApiResponse<ContractDetailView> resubmit(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.resubmit(id, user));
    }

    @GetMapping("/statistics")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<Map<String, Object>> statistics() {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(statisticsService.getStatistics(user));
    }

    @GetMapping("/statistics/contracts/status")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<Map<String, Object>> contractStatusStatistics() {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(statisticsService.getStatistics(user));
    }

    @GetMapping("/statistics/tasks/my")
    @RequirePermission({"contract:assign", "contract:countersign", "contract:approve", "contract:sign", "contract:update"})
    public ApiResponse<Map<String, Object>> myTaskStatistics() {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(statisticsService.getMyTaskStatistics(user));
    }

    @GetMapping("/statistics/contracts/monthly")
    @RequirePermission("contract:query")
    public ApiResponse<List<Map<String, Object>>> monthlyContractStatistics() {
        authService.requireUser();
        return ApiResponse.ok(statisticsService.getMonthlyStatistics());
    }

}
