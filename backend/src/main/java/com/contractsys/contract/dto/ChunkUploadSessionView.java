package com.contractsys.contract.dto;

import java.util.List;

public record ChunkUploadSessionView(
        String uploadId,
        Long contractId,
        String originalName,
        long fileSize,
        String contentType,
        int chunkSize,
        int totalChunks,
        List<Integer> uploadedChunks
) {
}
