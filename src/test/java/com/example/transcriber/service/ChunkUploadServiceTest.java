package com.example.transcriber.service;

import com.example.transcriber.dto.ChunkUploadInitRequest;
import com.example.transcriber.dto.ChunkUploadInitResponse;
import com.example.transcriber.dto.ChunkUploadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChunkUploadServiceTest {

    private static final String OWNER = "user-1";
    private static final String OTHER_USER = "user-2";

    @TempDir
    Path tempDir;

    private ChunkUploadService service;

    @BeforeEach
    void setUp() {
        service = new ChunkUploadService();
        ReflectionTestUtils.setField(service, "tempDirectory", tempDir.resolve("uploads").toString());
        ReflectionTestUtils.setField(service, "chunkSize", 1024L);
        ReflectionTestUtils.setField(service, "sessionTimeout", 3_600_000L);
        service.init();
    }

    @Test
    void initCreatesTempDirectory() {
        assertTrue(Files.isDirectory(tempDir.resolve("uploads")));
    }

    @Test
    void initializeUploadCreatesPendingSession() {
        ChunkUploadInitResponse response = service.initializeUpload(request("talk.mp3", 2), OWNER);

        assertNotNull(response.getUploadId());
        assertEquals(1024L, response.getChunkSize());
        assertTrue(Files.isDirectory(tempDir.resolve("uploads").resolve(response.getUploadId())));

        ChunkUploadStatus status = service.getStatus(response.getUploadId(), OWNER);
        assertEquals("PENDING", status.getStatus());
        assertEquals(2, status.getTotalChunks());
        assertEquals("talk.mp3", status.getFileName());
        assertTrue(status.getReceivedChunks().isEmpty());
    }

    @Test
    void fileNameIsSanitized() {
        String uploadId = service.initializeUpload(request("../../etc/pass wd.mp3", 1), OWNER).getUploadId();

        assertEquals(".._.._etc_pass_wd.mp3", service.getStatus(uploadId, OWNER).getFileName());
    }

    @Test
    void missingOrEmptyFileNameFallsBackToUpload() {
        String nullName = service.initializeUpload(request(null, 1), OWNER).getUploadId();
        String emptyName = service.initializeUpload(request("", 1), OWNER).getUploadId();

        assertEquals("upload", service.getStatus(nullName, OWNER).getFileName());
        assertEquals("upload", service.getStatus(emptyName, OWNER).getFileName());
    }

    @Test
    void chunksAreAssembledInOrderAndChunkFilesRemoved() throws Exception {
        String uploadId = service.initializeUpload(request("talk.mp3", 3), OWNER).getUploadId();

        // Out of order on purpose: assembly follows chunk index, not arrival order
        service.receiveChunk(uploadId, 2, chunk("C"), OWNER);
        service.receiveChunk(uploadId, 0, chunk("A"), OWNER);
        service.receiveChunk(uploadId, 1, chunk("B"), OWNER);
        assertEquals("IN_PROGRESS", service.getStatus(uploadId, OWNER).getStatus());
        assertEquals(Set.of(0, 1, 2), service.getStatus(uploadId, OWNER).getReceivedChunks());

        File assembled = service.assembleFile(uploadId, OWNER);

        assertEquals("ABC", Files.readString(assembled.toPath()));
        assertEquals("talk.mp3", assembled.getName());
        assertEquals("COMPLETE", service.getStatus(uploadId, OWNER).getStatus());
        assertEquals(assembled, service.getAssembledFile(uploadId, OWNER));
        try (var files = Files.list(assembled.getParentFile().toPath())) {
            assertEquals(1, files.count(), "only the assembled file should remain");
        }
    }

    @Test
    void assembleFailsWhenChunksAreMissing() throws Exception {
        String uploadId = service.initializeUpload(request("talk.mp3", 2), OWNER).getUploadId();
        service.receiveChunk(uploadId, 0, chunk("A"), OWNER);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.assembleFile(uploadId, OWNER));
        assertEquals("Missing chunks: received 1 of 2", e.getMessage());
    }

    @Test
    void chunkIndexOutsideRangeIsRejected() {
        String uploadId = service.initializeUpload(request("talk.mp3", 2), OWNER).getUploadId();

        assertThrows(IllegalArgumentException.class, () -> service.receiveChunk(uploadId, -1, chunk("A"), OWNER));
        assertThrows(IllegalArgumentException.class, () -> service.receiveChunk(uploadId, 2, chunk("A"), OWNER));
    }

    @Test
    void otherUsersCannotAccessAnUpload() {
        String uploadId = service.initializeUpload(request("talk.mp3", 1), OWNER).getUploadId();

        assertThrows(IllegalArgumentException.class,
                () -> service.receiveChunk(uploadId, 0, chunk("A"), OTHER_USER));
        assertThrows(IllegalArgumentException.class, () -> service.assembleFile(uploadId, OTHER_USER));
        assertNull(service.getStatus(uploadId, OTHER_USER));
        assertNull(service.getAssembledFile(uploadId, OTHER_USER));

        service.cancelUpload(uploadId, OTHER_USER);
        assertNotNull(service.getStatus(uploadId, OWNER), "another user's cancel must not remove the upload");
    }

    @Test
    void unknownOrMalformedUploadIdsAreRejected() {
        String unknown = "00000000-0000-0000-0000-000000000000";

        assertNull(service.getStatus(unknown, OWNER));
        assertNull(service.getStatus("../../etc", OWNER));
        assertNull(service.getStatus(null, OWNER));
        assertThrows(IllegalArgumentException.class, () -> service.receiveChunk("../x", 0, chunk("A"), OWNER));
    }

    @Test
    void cancelUploadRemovesSessionAndFiles() throws Exception {
        String uploadId = service.initializeUpload(request("talk.mp3", 2), OWNER).getUploadId();
        service.receiveChunk(uploadId, 0, chunk("A"), OWNER);
        Path uploadDir = tempDir.resolve("uploads").resolve(uploadId);

        service.cancelUpload(uploadId, OWNER);

        assertNull(service.getStatus(uploadId, OWNER));
        assertFalse(Files.exists(uploadDir));
    }

    @Test
    void cleanupOfUnknownUploadIsANoOp() {
        assertDoesNotThrow(() -> service.cleanup("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void expiredSessionsAreCleanedUp() throws Exception {
        String expired = service.initializeUpload(request("old.mp3", 1), OWNER).getUploadId();
        String active = service.initializeUpload(request("new.mp3", 1), OWNER).getUploadId();
        service.receiveChunk(active, 0, chunk("A"), OWNER);
        backdate(expired, 2 * 3_600_000L);

        service.cleanupExpiredSessions();

        assertNull(service.getStatus(expired, OWNER));
        assertFalse(Files.exists(tempDir.resolve("uploads").resolve(expired)));
        assertNotNull(service.getStatus(active, OWNER));
    }

    private void backdate(String uploadId, long millis) {
        @SuppressWarnings("unchecked")
        Map<String, Object> sessions = (Map<String, Object>) ReflectionTestUtils.getField(service, "sessions");
        Object session = sessions.get(uploadId);
        ReflectionTestUtils.setField(session, "createdAt", System.currentTimeMillis() - millis);
    }

    private static ChunkUploadInitRequest request(String fileName, int totalChunks) {
        ChunkUploadInitRequest request = new ChunkUploadInitRequest();
        request.setFileName(fileName);
        request.setTotalChunks(totalChunks);
        request.setTotalSize(totalChunks * 1024L);
        request.setMimeType("audio/mpeg");
        return request;
    }

    private static MockMultipartFile chunk(String content) {
        return new MockMultipartFile("chunk", "blob", "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8));
    }
}
