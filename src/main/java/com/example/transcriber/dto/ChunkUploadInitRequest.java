package com.example.transcriber.dto;

import lombok.Data;

@Data
public class ChunkUploadInitRequest {
    private String fileName;
    private long totalSize;
    private int totalChunks;
    private String mimeType;
}
