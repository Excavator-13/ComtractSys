package com.contractsys.contract.dto;

import com.contractsys.contract.Attachment;

import java.time.LocalDateTime;

public record AttachmentView(
        Long id,
        String originalName,
        long fileSize,
        LocalDateTime uploadedAt,
        String uploaderName
) {
    public static AttachmentView from(Attachment attachment) {
        return new AttachmentView(
                attachment.getId(),
                attachment.getOriginalName(),
                attachment.getFileSize(),
                attachment.getUploadedAt(),
                attachment.getUploader().getDisplayName()
        );
    }
}
