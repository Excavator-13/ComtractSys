package com.contractsys.contract;

import com.contractsys.common.ApiException;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.contract.dto.AttachmentView;
import com.contractsys.user.SysUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class ContractAttachmentService {
    private final ContractAccessGuard accessGuard;
    private final FileStorageService fileStorageService;
    private final AttachmentRepository attachmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ContractAttachmentService(ContractAccessGuard accessGuard,
                                     FileStorageService fileStorageService,
                                     AttachmentRepository attachmentRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.accessGuard = accessGuard;
        this.fileStorageService = fileStorageService;
        this.attachmentRepository = attachmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AttachmentView upload(Long contractId, MultipartFile file, SysUser user) {
        Contract contract = accessGuard.getContract(contractId);
        accessGuard.ensureCanModifyContract(contract, user);
        ensureAttachmentsMutable(contract);
        Attachment attachment = save(contract, file, user);
        return AttachmentView.from(attachment);
    }

    @Transactional
    public void uploadAll(Long contractId, List<MultipartFile> files, SysUser user) {
        if (files == null) {
            return;
        }
        Contract contract = accessGuard.getContract(contractId);
        accessGuard.ensureCanModifyContract(contract, user);
        ensureAttachmentsMutable(contract);
        files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .forEach(file -> save(contract, file, user));
    }

    public List<AttachmentView> list(Long contractId, SysUser user) {
        accessGuard.ensureCanViewContract(contractId, user);
        return attachmentRepository.findByContractIdOrderByUploadedAtDesc(contractId).stream()
                .map(AttachmentView::from)
                .toList();
    }

    public AttachmentResource download(Long attachmentId, SysUser user) {
        Attachment attachment = getAttachmentForView(attachmentId, user);
        return resource(attachment, MediaType.APPLICATION_OCTET_STREAM);
    }

    public AttachmentResource preview(Long attachmentId, SysUser user) {
        Attachment attachment = getAttachmentForView(attachmentId, user);
        if (!isPreviewable(attachment.getOriginalName())) {
            throw ApiException.badRequest("该附件类型暂不支持预览");
        }
        MediaType contentType = MediaType.parseMediaType(attachment.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : attachment.getContentType());
        return resource(attachment, contentType);
    }

    @Transactional
    public void delete(Long attachmentId, SysUser user) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ApiException.notFound("附件不存在"));
        accessGuard.ensureCanModifyContract(attachment.getContract(), user);
        ensureAttachmentsMutable(attachment.getContract());
        Path filePath = fileStorageService.resolve(attachment.getStoredName());
        attachmentRepository.delete(attachment);
        eventPublisher.publishEvent(new OperationLogEvent(user, "CONTRACT", "删除附件", "ATTACHMENT", attachment.getId(),
                attachment.getOriginalName()));
        deleteFileAfterCommit(filePath);
    }

    private Attachment save(Contract contract, MultipartFile file, SysUser user) {
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        Attachment attachment = new Attachment();
        attachment.setContract(contract);
        attachment.setOriginalName(stored.originalName());
        attachment.setStoredName(stored.storedName());
        attachment.setContentType(stored.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : stored.contentType());
        attachment.setFileSize(stored.fileSize());
        attachment.setUploader(user);
        attachmentRepository.save(attachment);
        eventPublisher.publishEvent(new OperationLogEvent(user, "CONTRACT", "上传附件", "ATTACHMENT", attachment.getId(),
                contract.getContractNo() + " " + attachment.getOriginalName()));
        return attachment;
    }

    private Attachment getAttachmentForView(Long attachmentId, SysUser user) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> ApiException.notFound("附件不存在"));
        accessGuard.ensureCanViewContract(attachment.getContract(), user);
        return attachment;
    }

    private void ensureAttachmentsMutable(Contract contract) {
        if (!List.of(
                ContractStatus.DRAFT,
                ContractStatus.ASSIGNED,
                ContractStatus.COUNTERSIGNED,
                ContractStatus.REJECTED,
                ContractStatus.RETURNED
        ).contains(contract.getStatus())) {
            throw ApiException.conflict("合同定稿后不能修改附件");
        }
    }

    private AttachmentResource resource(Attachment attachment, MediaType contentType) {
        Path filePath = fileStorageService.resolve(attachment.getStoredName());
        if (!Files.exists(filePath)) {
            throw ApiException.notFound("附件文件不存在");
        }
        return new AttachmentResource(new FileSystemResource(filePath), attachment.getOriginalName(), contentType);
    }

    private boolean isPreviewable(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        return lower.endsWith(".pdf")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".gif")
                || lower.endsWith(".bmp");
    }

    private void deleteFileAfterCommit(Path filePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletePhysicalFile(filePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deletePhysicalFile(filePath);
            }
        });
    }

    private void deletePhysicalFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }

    public record AttachmentResource(Resource resource, String filename, MediaType contentType) {
    }
}
