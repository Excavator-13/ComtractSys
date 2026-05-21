package com.contractsys.contract;

import com.contractsys.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path uploadDir;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "doc", "docx", "jpg", "jpeg", "png", "bmp", "gif", "pdf"
    );
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadPath) {
        this.uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + uploadDir, e);
        }
    }

    public StoredFile store(MultipartFile file) {
        if (file.isEmpty()) {
            throw ApiException.badRequest("文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.badRequest("文件大小不能超过 10MB");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw ApiException.badRequest("文件名无效");
        }
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw ApiException.badRequest("不支持的文件格式: " + ext + "，支持: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
        String storedName = UUID.randomUUID() + "." + ext;
        Path target = uploadDir.resolve(storedName);
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }
        return new StoredFile(originalName, storedName, file.getContentType(), file.getSize());
    }

    public Path resolve(String storedName) {
        return uploadDir.resolve(storedName);
    }

    public record StoredFile(String originalName, String storedName, String contentType, long fileSize) {}
}
