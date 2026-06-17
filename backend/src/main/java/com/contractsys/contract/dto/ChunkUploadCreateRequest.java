package com.contractsys.contract.dto;

public record ChunkUploadCreateRequest(
        String originalName,
        long fileSize,
        String contentType,
        int chunkSize,
        int totalChunks
) {
}
