package com.contractsys.contract;

import com.contractsys.common.ApiException;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.contract.dto.AttachmentView;
import com.contractsys.contract.dto.ChunkUploadCreateRequest;
import com.contractsys.contract.dto.ChunkUploadSessionView;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class ContractAttachmentService {
    private static final int DEFAULT_CHUNK_SIZE = 1024 * 1024;
    private static final int MAX_CHUNKS = 200;

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
        ensureAttachmentsMutable(contract, user);
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
        ensureAttachmentsMutable(contract, user);
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

    public ChunkUploadSessionView createChunkSession(Long contractId, ChunkUploadCreateRequest request, SysUser user) {
        Contract contract = accessGuard.getContract(contractId);
        accessGuard.ensureCanModifyContract(contract, user);
        ensureAttachmentsMutable(contract, user);
        fileStorageService.validateMetadata(request.originalName(), request.fileSize());
        int chunkSize = request.chunkSize() > 0 ? request.chunkSize() : DEFAULT_CHUNK_SIZE;
        int totalChunks = request.totalChunks() > 0
                ? request.totalChunks()
                : (int) Math.ceil((double) request.fileSize() / chunkSize);
        if (chunkSize <= 0 || totalChunks <= 0 || totalChunks > MAX_CHUNKS) {
            throw ApiException.badRequest("分片参数不合法");
        }
        if ((long) (totalChunks - 1) * chunkSize >= request.fileSize()) {
            throw ApiException.badRequest("分片数量与文件大小不匹配");
        }
        String uploadId = UUID.randomUUID().toString();
        Path sessionDir = sessionDir(uploadId);
        try {
            Files.createDirectories(sessionDir);
            Properties props = new Properties();
            props.setProperty("contractId", String.valueOf(contractId));
            props.setProperty("userId", String.valueOf(user.getId()));
            props.setProperty("originalName", request.originalName());
            props.setProperty("fileSize", String.valueOf(request.fileSize()));
            props.setProperty("contentType", request.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : request.contentType());
            props.setProperty("chunkSize", String.valueOf(chunkSize));
            props.setProperty("totalChunks", String.valueOf(totalChunks));
            props.setProperty("createdAt", Instant.now().toString());
            try (OutputStream out = Files.newOutputStream(sessionDir.resolve("manifest.properties"))) {
                props.store(out, "contract attachment chunk upload");
            }
        } catch (IOException e) {
            throw new RuntimeException("创建分片上传会话失败", e);
        }
        return sessionView(uploadId, props(request.originalName(), request.fileSize(), request.contentType(), contractId, user.getId(), chunkSize, totalChunks));
    }

    public ChunkUploadSessionView chunkSession(String uploadId, SysUser user) {
        Properties props = loadSession(uploadId);
        Contract contract = accessGuard.getContract(Long.parseLong(props.getProperty("contractId")));
        accessGuard.ensureCanModifyContract(contract, user);
        ensureChunkOwner(props, user);
        ensureAttachmentsMutable(contract, user);
        return sessionView(uploadId, props);
    }

    public ChunkUploadSessionView uploadChunk(String uploadId, int index, MultipartFile chunk, SysUser user) {
        if (chunk == null || chunk.isEmpty()) {
            throw ApiException.badRequest("分片为空");
        }
        Properties props = loadSession(uploadId);
        Contract contract = accessGuard.getContract(Long.parseLong(props.getProperty("contractId")));
        accessGuard.ensureCanModifyContract(contract, user);
        ensureChunkOwner(props, user);
        ensureAttachmentsMutable(contract, user);
        int totalChunks = Integer.parseInt(props.getProperty("totalChunks"));
        if (index < 0 || index >= totalChunks) {
            throw ApiException.badRequest("分片序号不合法");
        }
        Path target = sessionDir(uploadId).resolve(index + ".part");
        try {
            chunk.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("保存分片失败", e);
        }
        return sessionView(uploadId, props);
    }

    @Transactional
    public AttachmentView completeChunkSession(String uploadId, SysUser user) {
        Properties props = loadSession(uploadId);
        Contract contract = accessGuard.getContract(Long.parseLong(props.getProperty("contractId")));
        accessGuard.ensureCanModifyContract(contract, user);
        ensureChunkOwner(props, user);
        ensureAttachmentsMutable(contract, user);
        int totalChunks = Integer.parseInt(props.getProperty("totalChunks"));
        List<Integer> uploaded = uploadedChunks(uploadId, totalChunks);
        if (uploaded.size() != totalChunks) {
            throw ApiException.conflict("分片尚未上传完整");
        }
        Path merged = sessionDir(uploadId).resolve("merged.tmp");
        try (OutputStream out = Files.newOutputStream(merged)) {
            for (int i = 0; i < totalChunks; i++) {
                Path part = sessionDir(uploadId).resolve(i + ".part");
                try (InputStream in = Files.newInputStream(part)) {
                    in.transferTo(out);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("合并分片失败", e);
        }
        long expectedSize = Long.parseLong(props.getProperty("fileSize"));
        try {
            if (Files.size(merged) != expectedSize) {
                throw ApiException.badRequest("合并文件大小不匹配");
            }
        } catch (IOException e) {
            throw new RuntimeException("读取合并文件失败", e);
        }
        FileStorageService.StoredFile stored = fileStorageService.store(
                merged,
                props.getProperty("originalName"),
                props.getProperty("contentType"),
                expectedSize);
        Attachment attachment = persistStored(contract, stored, user);
        deleteDirectoryAfterCommit(sessionDir(uploadId));
        return AttachmentView.from(attachment);
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
        ensureAttachmentsMutable(attachment.getContract(), user);
        Path filePath = fileStorageService.resolve(attachment.getStoredName());
        attachmentRepository.delete(attachment);
        eventPublisher.publishEvent(new OperationLogEvent(user, "CONTRACT", "删除附件", "ATTACHMENT", attachment.getId(),
                attachment.getOriginalName()));
        deleteFileAfterCommit(filePath);
    }

    private Attachment save(Contract contract, MultipartFile file, SysUser user) {
        FileStorageService.StoredFile stored = fileStorageService.store(file);
        return persistStored(contract, stored, user);
    }

    private Attachment persistStored(Contract contract, FileStorageService.StoredFile stored, SysUser user) {
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

    private void ensureAttachmentsMutable(Contract contract, SysUser user) {
        if (!contract.getDrafter().getId().equals(user.getId())) {
            throw ApiException.forbidden("只有起草人可以修改附件");
        }
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw ApiException.conflict("仅起草阶段可以修改附件");
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

    private Path sessionDir(String uploadId) {
        if (uploadId == null || !uploadId.matches("[a-fA-F0-9\\-]{36}")) {
            throw ApiException.badRequest("上传会话无效");
        }
        return fileStorageService.chunksDir().resolve(uploadId);
    }

    private Properties loadSession(String uploadId) {
        Path manifest = sessionDir(uploadId).resolve("manifest.properties");
        if (!Files.exists(manifest)) {
            throw ApiException.notFound("上传会话不存在");
        }
        try (InputStream in = Files.newInputStream(manifest)) {
            Properties props = new Properties();
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new RuntimeException("读取上传会话失败", e);
        }
    }

    private Properties props(String originalName, long fileSize, String contentType, Long contractId, Long userId,
                             int chunkSize, int totalChunks) {
        Properties props = new Properties();
        props.setProperty("contractId", String.valueOf(contractId));
        props.setProperty("userId", String.valueOf(userId));
        props.setProperty("originalName", originalName);
        props.setProperty("fileSize", String.valueOf(fileSize));
        props.setProperty("contentType", contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType);
        props.setProperty("chunkSize", String.valueOf(chunkSize));
        props.setProperty("totalChunks", String.valueOf(totalChunks));
        return props;
    }

    private void ensureChunkOwner(Properties props, SysUser user) {
        if (!String.valueOf(user.getId()).equals(props.getProperty("userId"))) {
            throw ApiException.forbidden("不能操作其他用户的上传会话");
        }
    }

    private ChunkUploadSessionView sessionView(String uploadId, Properties props) {
        int totalChunks = Integer.parseInt(props.getProperty("totalChunks"));
        return new ChunkUploadSessionView(
                uploadId,
                Long.parseLong(props.getProperty("contractId")),
                props.getProperty("originalName"),
                Long.parseLong(props.getProperty("fileSize")),
                props.getProperty("contentType"),
                Integer.parseInt(props.getProperty("chunkSize")),
                totalChunks,
                uploadedChunks(uploadId, totalChunks)
        );
    }

    private List<Integer> uploadedChunks(String uploadId, int totalChunks) {
        Path dir = sessionDir(uploadId);
        List<Integer> uploaded = new ArrayList<>();
        IntStream.range(0, totalChunks).forEach(index -> {
            if (Files.exists(dir.resolve(index + ".part"))) {
                uploaded.add(index);
            }
        });
        return uploaded;
    }

    private void deleteDirectoryAfterCommit(Path dir) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteDirectory(dir);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteDirectory(dir);
            }
        });
    }

    private void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    public record AttachmentResource(Resource resource, String filename, MediaType contentType) {
    }
}
