package com.contractsys.contract;

import com.contractsys.auth.AuthService;
import com.contractsys.auth.RequirePermission;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import com.contractsys.contract.dto.*;
import com.contractsys.log.OperationLog;
import com.contractsys.log.OperationLogService;
import com.contractsys.user.SysUser;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ContractController {
    private final ContractService contractService;
    private final AuthService authService;
    private final FileStorageService fileStorageService;
    private final AttachmentRepository attachmentRepository;
    private final OperationLogService operationLogService;

    public ContractController(ContractService contractService, AuthService authService,
                              FileStorageService fileStorageService, AttachmentRepository attachmentRepository,
                              OperationLogService operationLogService) {
        this.contractService = contractService;
        this.authService = authService;
        this.fileStorageService = fileStorageService;
        this.attachmentRepository = attachmentRepository;
        this.operationLogService = operationLogService;
    }

    @GetMapping("/contracts")
    @RequirePermission("contract:view")
    public ApiResponse<PageResponse<ContractView>> list(@RequestParam(defaultValue = "") String keyword,
                                                        @RequestParam(defaultValue = "") String status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(PageResponse.from(contractService.list(keyword, status, page, size, user)));
    }

    @GetMapping("/contracts/query")
    @RequirePermission("contract:query")
    public ApiResponse<PageResponse<ContractView>> query(@RequestParam(defaultValue = "") String keyword,
                                                         @RequestParam(defaultValue = "") String status,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        authService.requireUser();
        return ApiResponse.ok(PageResponse.from(contractService.query(keyword, status, page, size)));
    }

    @GetMapping("/contracts/{id}")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<ContractDetailView> detail(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.detail(id, user));
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
        if (files != null) {
            Contract contract = contractService.getContract(created.id());
            files.stream().filter(file -> file != null && !file.isEmpty())
                    .forEach(file -> saveAttachment(contract, file, user));
        }
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
        return ApiResponse.ok(contractService.myTasks(user));
    }

    @GetMapping("/contracts/{id}/process")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<Map<String, Object>> process(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.process(id, user));
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

    @GetMapping("/logs")
    @RequirePermission("log:view")
    public ApiResponse<PageResponse<OperationLog>> logs(@RequestParam(defaultValue = "") String keyword,
                                                        @RequestParam(defaultValue = "") String module,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        authService.requireUser();
        return ApiResponse.ok(PageResponse.from(operationLogService.list(keyword, module, page, size)));
    }

    @GetMapping("/logs/export")
    @RequirePermission("log:view")
    public ResponseEntity<Resource> exportLogs(@RequestParam(defaultValue = "") String keyword,
                                               @RequestParam(defaultValue = "") String module) {
        authService.requireUser();
        byte[] csv = operationLogService.export(keyword, module);
        Resource resource = new org.springframework.core.io.ByteArrayResource(csv);
        String filename = "logs_" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(resource);
    }

    @PostMapping("/contracts/{id}/attachments")
    @RequirePermission("contract:update")
    public ApiResponse<Map<String, Object>> uploadAttachment(@PathVariable Long id,
                                                              @RequestParam("file") MultipartFile file) {
        SysUser user = authService.requireUser();
        Contract contract = contractService.getContract(id);
        contractService.ensureCanModifyContract(contract.getId(), user);
        Attachment attachment = saveAttachment(contract, file, user);
        return ApiResponse.ok("上传成功", Map.of("id", attachment.getId(), "originalName", attachment.getOriginalName()));
    }

    @GetMapping("/contracts/{id}/attachments")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<List<Map<String, Object>>> listAttachments(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        contractService.ensureCanViewContract(id, user);
        List<Map<String, Object>> list = attachmentRepository.findByContractIdOrderByUploadedAtDesc(id).stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "originalName", a.getOriginalName(),
                        "fileSize", a.getFileSize(),
                        "uploadedAt", a.getUploadedAt(),
                        "uploaderName", a.getUploader().getDisplayName()
                )).toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/attachments/{id}/download")
    @RequirePermission({"contract:view", "contract:query"})
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> com.contractsys.common.ApiException.notFound("附件不存在"));
        contractService.ensureCanModifyContract(attachment.getContract().getId(), user);
        Path filePath = fileStorageService.resolve(attachment.getStoredName());
        if (!Files.exists(filePath)) {
            throw com.contractsys.common.ApiException.notFound("附件文件不存在");
        }
        Resource resource = new FileSystemResource(filePath);
        String encodedName = URLEncoder.encode(attachment.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/attachments/{id}")
    @RequirePermission("contract:update")
    public ApiResponse<Void> deleteAttachment(@PathVariable Long id) {
        SysUser user = authService.requireUser();
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> com.contractsys.common.ApiException.notFound("附件不存在"));
        contractService.ensureCanViewContract(attachment.getContract().getId(), user);
        try {
            Files.deleteIfExists(fileStorageService.resolve(attachment.getStoredName()));
        } catch (IOException ignored) {}
        attachmentRepository.delete(attachment);
        operationLogService.record(user, "CONTRACT", "删除附件", "ATTACHMENT", attachment.getId(),
                attachment.getOriginalName());
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
        return ApiResponse.ok(contractService.getStatistics(user));
    }

    @GetMapping("/statistics/contracts/status")
    @RequirePermission({"contract:view", "contract:query"})
    public ApiResponse<Map<String, Object>> contractStatusStatistics() {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.getStatistics(user));
    }

    @GetMapping("/statistics/tasks/my")
    @RequirePermission({"contract:assign", "contract:countersign", "contract:approve", "contract:sign", "contract:update"})
    public ApiResponse<Map<String, Object>> myTaskStatistics() {
        SysUser user = authService.requireUser();
        return ApiResponse.ok(contractService.getMyTaskStatistics(user));
    }

    @GetMapping("/statistics/contracts/monthly")
    @RequirePermission("contract:query")
    public ApiResponse<List<Map<String, Object>>> monthlyContractStatistics() {
        authService.requireUser();
        return ApiResponse.ok(contractService.getMonthlyStatistics());
    }

    private Attachment saveAttachment(Contract contract, MultipartFile file, SysUser user) {
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        Attachment attachment = new Attachment();
        attachment.setContract(contract);
        attachment.setOriginalName(stored.originalName());
        attachment.setStoredName(stored.storedName());
        attachment.setContentType(stored.contentType());
        attachment.setFileSize(stored.fileSize());
        attachment.setUploader(user);
        attachmentRepository.save(attachment);
        operationLogService.record(user, "CONTRACT", "上传附件", "ATTACHMENT", attachment.getId(),
                contract.getContractNo() + " " + attachment.getOriginalName());
        return attachment;
    }
}
