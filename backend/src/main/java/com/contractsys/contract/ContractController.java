package com.contractsys.contract;

import com.contractsys.auth.AuthService;
import com.contractsys.common.ApiResponse;
import com.contractsys.common.PageResponse;
import com.contractsys.contract.dto.*;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ContractController {
    private final ContractService contractService;
    private final AuthService authService;
    private final FileStorageService fileStorageService;
    private final AttachmentRepository attachmentRepository;

    public ContractController(ContractService contractService, AuthService authService,
                              FileStorageService fileStorageService, AttachmentRepository attachmentRepository) {
        this.contractService = contractService;
        this.authService = authService;
        this.fileStorageService = fileStorageService;
        this.attachmentRepository = attachmentRepository;
    }

    @GetMapping("/contracts")
    public ApiResponse<PageResponse<ContractView>> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @RequestParam(defaultValue = "") String keyword,
                                                        @RequestParam(defaultValue = "") String status,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        authService.requireUser(authorization);
        return ApiResponse.ok(PageResponse.from(contractService.list(keyword, status, page, size)));
    }

    @GetMapping("/contracts/{id}")
    public ApiResponse<ContractDetailView> detail(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @PathVariable Long id) {
        authService.requireUser(authorization);
        return ApiResponse.ok(contractService.detail(id));
    }

    @PostMapping("/contracts")
    public ApiResponse<ContractView> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @Valid @RequestBody ContractCreateRequest request) {
        SysUser user = authService.requireUser(authorization);
        return ApiResponse.ok("起草成功", contractService.create(request, user));
    }

    @PostMapping("/contracts/{id}/assign")
    public ApiResponse<ContractDetailView> assign(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody AssignRequest request) {
        SysUser user = authService.requireUser(authorization);
        return ApiResponse.ok(contractService.assign(id, request, user));
    }

    @GetMapping("/tasks/my")
    public ApiResponse<List<TaskView>> myTasks(@RequestHeader(value = "Authorization", required = false) String authorization) {
        SysUser user = authService.requireUser(authorization);
        return ApiResponse.ok(contractService.myTasks(user));
    }

    @PostMapping("/contracts/{id}/countersign")
    public ApiResponse<ContractDetailView> countersign(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody OpinionRequest request) {
        SysUser user = authService.requireUser(authorization);
        return ApiResponse.ok(contractService.countersign(id, request, user));
    }

    @PostMapping("/contracts/{id}/finalize")
    public ApiResponse<ContractDetailView> finalizeContract(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                           @PathVariable Long id,
                                                           @Valid @RequestBody FinalizeRequest request) {
        SysUser user = authService.requireUser(authorization);
        return ApiResponse.ok(contractService.finalizeContract(id, request, user));
    }

    @PostMapping("/contracts/{id}/approve")
    public ApiResponse<ContractDetailView> approve(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody ApproveRequest request) {
        SysUser user = authService.requireUser(authorization);
        return ApiResponse.ok(contractService.approve(id, request, user));
    }

    @PostMapping("/contracts/{id}/sign")
    public ApiResponse<ContractDetailView> sign(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @PathVariable Long id,
                                                @Valid @RequestBody SignRequest request) {
        SysUser user = authService.requireUser(authorization);
        return ApiResponse.ok(contractService.sign(id, request, user));
    }

    @PutMapping("/contracts/{id}")
    public ApiResponse<ContractView> update(@RequestHeader(value = "Authorization", required = false) String authorization,
                                            @PathVariable Long id,
                                            @Valid @RequestBody ContractCreateRequest request) {
        SysUser user = authService.requireUser(authorization);
        return ApiResponse.ok(contractService.update(id, request, user));
    }

    @DeleteMapping("/contracts/{id}")
    public ApiResponse<Void> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @PathVariable Long id) {
        SysUser user = authService.requireUser(authorization);
        contractService.delete(id, user);
        return ApiResponse.ok(null);
    }

    @GetMapping("/logs")
    public ApiResponse<PageResponse<ContractStateHistory>> logs(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                  @RequestParam(defaultValue = "") String keyword,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "10") int size) {
        authService.requireUser(authorization);
        return ApiResponse.ok(PageResponse.from(contractService.logs(keyword, page, size)));
    }

    @GetMapping("/logs/export")
    public ResponseEntity<Resource> exportLogs(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @RequestParam(defaultValue = "") String keyword) throws Exception {
        authService.requireUser(authorization);
        byte[] csv = contractService.exportLogs(keyword);
        Resource resource = new org.springframework.core.io.ByteArrayResource(csv);
        String filename = "logs_" + java.time.LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(filename, StandardCharsets.UTF_8))
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(resource);
    }

    @PostMapping("/contracts/{id}/attachments")
    public ApiResponse<Map<String, Object>> uploadAttachment(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                              @PathVariable Long id,
                                                              @RequestParam("file") MultipartFile file) {
        SysUser user = authService.requireUser(authorization);
        Contract contract = contractService.getContract(id);
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        Attachment attachment = new Attachment();
        attachment.setContract(contract);
        attachment.setOriginalName(stored.originalName());
        attachment.setStoredName(stored.storedName());
        attachment.setContentType(stored.contentType());
        attachment.setFileSize(stored.fileSize());
        attachment.setUploader(user);
        attachmentRepository.save(attachment);
        return ApiResponse.ok("上传成功", Map.of("id", attachment.getId(), "originalName", attachment.getOriginalName()));
    }

    @GetMapping("/contracts/{id}/attachments")
    public ApiResponse<List<Map<String, Object>>> listAttachments(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                    @PathVariable Long id) {
        authService.requireUser(authorization);
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
    public ResponseEntity<Resource> downloadAttachment(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @PathVariable Long id) {
        authService.requireUser(authorization);
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> com.contractsys.common.ApiException.notFound("附件不存在"));
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
    public ApiResponse<Void> deleteAttachment(@RequestHeader(value = "Authorization", required = false) String authorization,
                                               @PathVariable Long id) {
        authService.requireUser(authorization);
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> com.contractsys.common.ApiException.notFound("附件不存在"));
        try {
            Files.deleteIfExists(fileStorageService.resolve(attachment.getStoredName()));
        } catch (IOException ignored) {}
        attachmentRepository.delete(attachment);
        return ApiResponse.ok(null);
    }

    @PostMapping("/contracts/{id}/resubmit")
    public ApiResponse<ContractDetailView> resubmit(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                     @PathVariable Long id) {
        SysUser user = authService.requireUser(authorization);
        return ApiResponse.ok(contractService.resubmit(id, user));
    }

    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> statistics(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireUser(authorization);
        return ApiResponse.ok(contractService.getStatistics());
    }
}

