package com.contractsys.contract;

import com.contractsys.common.ApiException;
import com.contractsys.common.event.OperationLogEvent;
import com.contractsys.contract.dto.ContractTemplateView;
import com.contractsys.user.SysUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class ContractTemplateService {
    private final ContractTemplateRepository templateRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public ContractTemplateService(ContractTemplateRepository templateRepository,
                                   FileStorageService fileStorageService,
                                   ApplicationEventPublisher eventPublisher) {
        this.templateRepository = templateRepository;
        this.fileStorageService = fileStorageService;
        this.eventPublisher = eventPublisher;
    }

    public List<ContractTemplateView> listEnabled() {
        return templateRepository.findByEnabledTrueOrderByCreatedAtAsc().stream()
                .map(ContractTemplateView::from)
                .toList();
    }

    public List<ContractTemplateView> listAll() {
        return templateRepository.findAll().stream()
                .map(ContractTemplateView::from)
                .toList();
    }

    @Transactional
    public ContractTemplateView create(String name, String description, String content, MultipartFile file, SysUser operator) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("模板名称不能为空");
        }
        ContractTemplate template = new ContractTemplate();
        template.setName(name.trim());
        template.setDescription(description);
        template.setContent(content == null || content.isBlank() ? name.trim() : content);
        if (file != null && !file.isEmpty()) {
            FileStorageService.StoredFile stored = fileStorageService.store(file);
            template.setOriginalName(stored.originalName());
            template.setStoredName(stored.storedName());
            template.setContentType(stored.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : stored.contentType());
            template.setFileSize(stored.fileSize());
        }
        templateRepository.save(template);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "CONTRACT", "上传合同模板", "TEMPLATE", template.getId(),
                template.getName()));
        return ContractTemplateView.from(template);
    }

    @Transactional
    public ContractTemplateView setEnabled(Long id, boolean enabled, SysUser operator) {
        ContractTemplate template = find(id);
        template.setEnabled(enabled);
        templateRepository.save(template);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "CONTRACT", enabled ? "启用合同模板" : "停用合同模板",
                "TEMPLATE", template.getId(), template.getName()));
        return ContractTemplateView.from(template);
    }

    @Transactional
    public void delete(Long id, SysUser operator) {
        ContractTemplate template = find(id);
        Path filePath = template.getStoredName() == null ? null : fileStorageService.resolve(template.getStoredName());
        templateRepository.delete(template);
        eventPublisher.publishEvent(new OperationLogEvent(operator, "CONTRACT", "删除合同模板", "TEMPLATE", id,
                template.getName()));
        if (filePath != null) {
            deleteFileAfterCommit(filePath);
        }
    }

    public TemplateResource download(Long id) {
        ContractTemplate template = find(id);
        if (template.getStoredName() != null && !template.getStoredName().isBlank()) {
            Path filePath = fileStorageService.resolve(template.getStoredName());
            if (!Files.exists(filePath)) {
                throw ApiException.notFound("模板文件不存在");
            }
            MediaType contentType = MediaType.parseMediaType(template.getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : template.getContentType());
            return new TemplateResource(new FileSystemResource(filePath), template.getOriginalName(), contentType);
        }
        String filename = template.getName() + ".txt";
        byte[] bytes = (template.getContent() == null ? "" : template.getContent()).getBytes(StandardCharsets.UTF_8);
        return new TemplateResource(new ByteArrayResource(bytes), filename, MediaType.TEXT_PLAIN);
    }

    private ContractTemplate find(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("合同模板不存在"));
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

    public record TemplateResource(Resource resource, String filename, MediaType contentType) {
    }
}
