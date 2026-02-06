package com.example.transcriber.controller;

import com.example.transcriber.dto.ChunkUploadInitRequest;
import com.example.transcriber.dto.ChunkUploadInitResponse;
import com.example.transcriber.dto.ChunkUploadStatus;
import com.example.transcriber.service.ChunkUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class ChunkUploadController {

    private final ChunkUploadService chunkUploadService;

    /**
     * Initialize a new chunked upload session
     */
    @PostMapping("/init")
    public ResponseEntity<ChunkUploadInitResponse> initUpload(
            @RequestBody ChunkUploadInitRequest request,
            @AuthenticationPrincipal OidcUser user) {

        String userId = getUserId(user);
        ChunkUploadInitResponse response = chunkUploadService.initializeUpload(request, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Upload a single chunk
     */
    @PostMapping("/{uploadId}/chunk/{index}")
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @PathVariable String uploadId,
            @PathVariable int index,
            @RequestParam("chunk") MultipartFile chunk,
            @AuthenticationPrincipal OidcUser user) {

        String userId = getUserId(user);

        try {
            chunkUploadService.receiveChunk(uploadId, index, chunk, userId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "chunkIndex", index
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid chunk upload request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Failed to receive chunk {} for upload {}", index, uploadId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to store chunk: " + e.getMessage()
            ));
        }
    }

    /**
     * Complete the upload by assembling all chunks
     */
    @PostMapping("/{uploadId}/complete")
    public ResponseEntity<ChunkUploadStatus> completeUpload(
            @PathVariable String uploadId,
            @AuthenticationPrincipal OidcUser user) {

        String userId = getUserId(user);

        try {
            File assembledFile = chunkUploadService.assembleFile(uploadId, userId);
            ChunkUploadStatus status = chunkUploadService.getStatus(uploadId, userId);

            return ResponseEntity.ok(status);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid complete request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorStatus(uploadId, e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Cannot complete upload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(createErrorStatus(uploadId, e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to complete upload {}", uploadId, e);
            return ResponseEntity.internalServerError().body(createErrorStatus(uploadId, "Assembly failed: " + e.getMessage()));
        }
    }

    /**
     * Get upload status (useful for resume capability)
     */
    @GetMapping("/{uploadId}/status")
    public ResponseEntity<ChunkUploadStatus> getStatus(
            @PathVariable String uploadId,
            @AuthenticationPrincipal OidcUser user) {

        String userId = getUserId(user);
        ChunkUploadStatus status = chunkUploadService.getStatus(uploadId, userId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Cancel an upload and cleanup
     */
    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Map<String, Object>> cancelUpload(
            @PathVariable String uploadId,
            @AuthenticationPrincipal OidcUser user) {

        String userId = getUserId(user);
        chunkUploadService.cancelUpload(uploadId, userId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "uploadId", uploadId
        ));
    }

    private String getUserId(OidcUser user) {
        // Use subject claim as user identifier
        return user.getSubject();
    }

    private ChunkUploadStatus createErrorStatus(String uploadId, String error) {
        ChunkUploadStatus status = new ChunkUploadStatus();
        status.setUploadId(uploadId);
        status.setStatus("ERROR");
        status.setError(error);
        return status;
    }
}
