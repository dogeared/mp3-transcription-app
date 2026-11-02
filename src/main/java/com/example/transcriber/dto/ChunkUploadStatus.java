package com.example.transcriber.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadStatus {
    private String uploadId;
    private Set<Integer> receivedChunks;
    private int totalChunks;
    private String status; // PENDING, IN_PROGRESS, COMPLETE, ERROR
    private String error;
    private String fileName;
}
