package com.example.transcriber.service;

import com.example.transcriber.dto.ChunkUploadInitRequest;
import com.example.transcriber.dto.ChunkUploadInitResponse;
import com.example.transcriber.dto.ChunkUploadStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ChunkUploadService {

    @Value("${upload.temp.directory:${java.io.tmpdir}/mp3-transcriber-uploads}")
    private String tempDirectory;

    @Value("${upload.chunk.size:52428800}")
    private long chunkSize; // 50MB default

    @Value("${upload.session.timeout:3600000}")
    private long sessionTimeout; // 1 hour default

    private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        File dir = new File(tempDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        log.info("Chunk upload temp directory: {}", tempDirectory);
    }

    /**
     * Initialize a new upload session
     */
    public ChunkUploadInitResponse initializeUpload(ChunkUploadInitRequest request, String userId) {
        String uploadId = UUID.randomUUID().toString();

        UploadSession session = new UploadSession();
        session.uploadId = uploadId;
        session.fileName = sanitizeFileName(request.getFileName());
        session.totalSize = request.getTotalSize();
        session.totalChunks = request.getTotalChunks();
        session.mimeType = request.getMimeType();
        session.userId = userId;
        session.createdAt = System.currentTimeMillis();
        session.status = "PENDING";

        // Create directory for this upload's chunks
        File uploadDir = new File(tempDirectory, uploadId);
        uploadDir.mkdirs();
        session.uploadDir = uploadDir;

        sessions.put(uploadId, session);

        log.info("Initialized upload session {} for file {} ({} bytes, {} chunks) for user {}",
                uploadId, session.fileName, session.totalSize, session.totalChunks, userId);

        return new ChunkUploadInitResponse(uploadId, chunkSize);
    }

    /**
     * Receive and store a chunk
     */
    public void receiveChunk(String uploadId, int chunkIndex, MultipartFile chunkFile, String userId) throws IOException {
        UploadSession session = getSessionForUser(uploadId, userId);

        if (session == null) {
            throw new IllegalArgumentException("Upload session not found or access denied: " + uploadId);
        }

        if (chunkIndex < 0 || chunkIndex >= session.totalChunks) {
            throw new IllegalArgumentException("Invalid chunk index: " + chunkIndex);
        }

        // Write chunk to disk
        File chunkDestination = new File(session.uploadDir, "chunk_" + chunkIndex);
        try (InputStream is = chunkFile.getInputStream();
             OutputStream os = new FileOutputStream(chunkDestination)) {
            is.transferTo(os);
        }

        session.receivedChunks.add(chunkIndex);
        session.status = "IN_PROGRESS";
        session.lastActivityAt = System.currentTimeMillis();

        log.debug("Received chunk {} of {} for upload {}", chunkIndex + 1, session.totalChunks, uploadId);
    }

    /**
     * Assemble all chunks into the final file
     */
    public File assembleFile(String uploadId, String userId) throws IOException {
        UploadSession session = getSessionForUser(uploadId, userId);

        if (session == null) {
            throw new IllegalArgumentException("Upload session not found or access denied: " + uploadId);
        }

        // Verify all chunks received
        if (session.receivedChunks.size() != session.totalChunks) {
            throw new IllegalStateException(String.format(
                "Missing chunks: received %d of %d",
                session.receivedChunks.size(), session.totalChunks));
        }

        // Create final file
        File assembledFile = new File(session.uploadDir, session.fileName);

        try (RandomAccessFile raf = new RandomAccessFile(assembledFile, "rw")) {
            for (int i = 0; i < session.totalChunks; i++) {
                File chunkFile = new File(session.uploadDir, "chunk_" + i);
                byte[] chunkData = Files.readAllBytes(chunkFile.toPath());
                raf.write(chunkData);
            }
        }

        session.assembledFile = assembledFile;
        session.status = "COMPLETE";

        // Clean up chunk files but keep assembled file
        for (int i = 0; i < session.totalChunks; i++) {
            File chunkFile = new File(session.uploadDir, "chunk_" + i);
            chunkFile.delete();
        }

        log.info("Assembled file {} ({} bytes) for upload {}",
                session.fileName, assembledFile.length(), uploadId);

        return assembledFile;
    }

    /**
     * Get the assembled file for an upload
     */
    public File getAssembledFile(String uploadId, String userId) {
        UploadSession session = getSessionForUser(uploadId, userId);

        if (session == null) {
            return null;
        }

        return session.assembledFile;
    }

    /**
     * Get upload status
     */
    public ChunkUploadStatus getStatus(String uploadId, String userId) {
        UploadSession session = getSessionForUser(uploadId, userId);

        if (session == null) {
            return null;
        }

        return new ChunkUploadStatus(
                session.uploadId,
                session.receivedChunks,
                session.totalChunks,
                session.status,
                session.error,
                session.fileName
        );
    }

    /**
     * Cancel and cleanup an upload
     */
    public void cancelUpload(String uploadId, String userId) {
        UploadSession session = getSessionForUser(uploadId, userId);

        if (session != null) {
            cleanup(uploadId);
        }
    }

    /**
     * Clean up temp files for an upload
     */
    public void cleanup(String uploadId) {
        UploadSession session = sessions.remove(uploadId);

        if (session != null && session.uploadDir != null && session.uploadDir.exists()) {
            try {
                // Delete all files in the upload directory
                try (Stream<Path> paths = Files.walk(session.uploadDir.toPath())) {
                    paths.sorted(Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                }
                log.info("Cleaned up upload session {}", uploadId);
            } catch (IOException e) {
                log.warn("Failed to cleanup upload directory for {}", uploadId, e);
            }
        }
    }

    /**
     * Get session only if it belongs to the specified user
     */
    private UploadSession getSessionForUser(String uploadId, String userId) {
        UploadSession session = sessions.get(uploadId);

        if (session == null) {
            return null;
        }

        if (!session.userId.equals(userId)) {
            log.warn("User {} attempted to access upload {} owned by {}",
                    userId, uploadId, session.userId);
            return null;
        }

        return session;
    }

    /**
     * Sanitize filename to prevent path traversal
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "upload";
        }
        // Remove any path components and keep only the filename
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (sanitized.isEmpty()) {
            return "upload";
        }
        return sanitized;
    }

    /**
     * Cleanup expired sessions periodically
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();

        sessions.entrySet().removeIf(entry -> {
            UploadSession session = entry.getValue();
            long lastActivity = session.lastActivityAt > 0 ? session.lastActivityAt : session.createdAt;

            if (now - lastActivity > sessionTimeout) {
                log.info("Cleaning up expired upload session {}", session.uploadId);
                cleanup(session.uploadId);
                return true;
            }
            return false;
        });
    }

    /**
     * Internal class to track upload sessions
     */
    private static class UploadSession {
        String uploadId;
        String fileName;
        long totalSize;
        int totalChunks;
        String mimeType;
        String userId;
        long createdAt;
        long lastActivityAt;
        String status;
        String error;
        File uploadDir;
        File assembledFile;
        Set<Integer> receivedChunks = ConcurrentHashMap.newKeySet();
    }
}
