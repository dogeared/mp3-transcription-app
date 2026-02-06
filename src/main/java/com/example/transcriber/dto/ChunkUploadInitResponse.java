package com.example.transcriber.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadInitResponse {
    private String uploadId;
    private long chunkSize;
}
