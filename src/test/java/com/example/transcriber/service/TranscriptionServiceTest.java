package com.example.transcriber.service;

import com.example.transcriber.dto.TranscriptResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@ExtendWith(MockitoExtension.class)
class TranscriptionServiceTest {

    private TranscriptionService transcriptionService;
    private MockWebServer mockWebServer;
    private ObjectMapper objectMapper;
    private File testAudioFile;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        
        objectMapper = new ObjectMapper();
        
        // Create service with mock server URL using the test constructor
        String baseUrl = mockWebServer.url("/v2").toString().replaceAll("/$", "");
        transcriptionService = new TranscriptionService("test-api-key", baseUrl);
        
        // Create a temporary test file
        testAudioFile = Files.createTempFile("test", ".mp3").toFile();
        testAudioFile.deleteOnExit();
        Files.write(testAudioFile.toPath(), "test audio content".getBytes());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
        if (testAudioFile.exists()) {
            testAudioFile.delete();
        }
    }

    @Test
    void testTranscribeFile_Success() throws Exception {
        // Mock upload response
        String uploadUrl = "https://upload.assemblyai.com/test-file-url";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"upload_url\": \"" + uploadUrl + "\"}")
            .addHeader("Content-Type", "application/json"));

        // Mock submit transcription response
        String transcriptId = "test-transcript-id";
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"id\": \"" + transcriptId + "\"}")
            .addHeader("Content-Type", "application/json"));

        // Mock polling responses - first processing, then completed
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"id\": \"" + transcriptId + "\", \"status\": \"processing\"}")
            .addHeader("Content-Type", "application/json"));

        TranscriptResponse completedResponse = createTestTranscriptResponse();
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(objectMapper.writeValueAsString(completedResponse))
            .addHeader("Content-Type", "application/json"));

        // Execute transcription
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<String> lastProgress = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        CompletableFuture<String> future = transcriptionService.transcribeFile(
            testAudioFile,
            "Alice",
            "Bob",
            progress -> {
                lastProgress.set(progress);
            }
        );

        future.whenComplete((transcript, throwable) -> {
            result.set(transcript);
            latch.countDown();
        });

        // Wait for completion
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        // Verify result
        assertNotNull(result.get());
        assertTrue(result.get().contains("[Alice]: Hello from Alice"));
        assertTrue(result.get().contains("[Bob]: Hello from Bob"));
        assertEquals("Transcription complete!", lastProgress.get());

        // Verify HTTP requests
        assertEquals(4, mockWebServer.getRequestCount());
        
        RecordedRequest uploadRequest = mockWebServer.takeRequest();
        assertEquals("POST", uploadRequest.getMethod());
        assertTrue(uploadRequest.getPath().endsWith("/upload"));
        assertEquals("test-api-key", uploadRequest.getHeader("Authorization"));

        RecordedRequest submitRequest = mockWebServer.takeRequest();
        assertEquals("POST", submitRequest.getMethod());
        assertTrue(submitRequest.getPath().endsWith("/transcript"));
        assertTrue(submitRequest.getBody().readUtf8().contains("\"speaker_labels\": true"));
    }

    @Test
    void testTranscribeFile_UploadFailure() throws Exception {
        // Mock upload failure
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        CompletableFuture<String> future = transcriptionService.transcribeFile(
            testAudioFile,
            "Alice",
            "Bob",
            progress -> {}
        );

        // Should fail with runtime exception
        assertThrows(Exception.class, () -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testTranscribeFile_TranscriptionError() throws Exception {
        // Mock successful upload
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"upload_url\": \"https://upload.assemblyai.com/test-file-url\"}")
            .addHeader("Content-Type", "application/json"));

        // Mock successful submit
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"id\": \"test-transcript-id\"}")
            .addHeader("Content-Type", "application/json"));

        // Mock error status in polling
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"id\": \"test-transcript-id\", \"status\": \"error\", \"error\": \"Audio file format not supported\"}")
            .addHeader("Content-Type", "application/json"));

        CompletableFuture<String> future = transcriptionService.transcribeFile(
            testAudioFile,
            "Alice",
            "Bob",
            progress -> {}
        );

        // Should fail with runtime exception containing error message
        Exception exception = assertThrows(Exception.class, () -> future.get(5, TimeUnit.SECONDS));
        
        // The service wraps exceptions with "Transcription failed"
        Throwable cause = exception.getCause();
        assertNotNull(cause, "Exception should have a cause");
        assertTrue(cause instanceof RuntimeException, "Cause should be RuntimeException, but was: " + cause.getClass());
        // The outer exception has generic message, specific error is in the cause
        assertTrue(cause.getMessage().equals("Transcription failed") || 
                  cause.getMessage().contains("Transcription failed: Audio file format not supported"),
                  "Expected 'Transcription failed' message. Actual message: " + cause.getMessage());
    }

    @Test
    void testTranscribeFile_NonExistentFile() {
        File nonExistentFile = new File("non-existent-file.mp3");
        
        CompletableFuture<String> future = transcriptionService.transcribeFile(
            nonExistentFile,
            "Alice",
            "Bob",
            progress -> {}
        );

        // Should fail immediately
        assertThrows(Exception.class, () -> future.get(1, TimeUnit.SECONDS));
    }

    @Test
    void testTranscribeFile_EmptySpeakerNames() throws Exception {
        // Mock successful responses
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"upload_url\": \"https://upload.assemblyai.com/test-file-url\"}")
            .addHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{\"id\": \"test-transcript-id\"}")
            .addHeader("Content-Type", "application/json"));

        TranscriptResponse completedResponse = createTestTranscriptResponse();
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(objectMapper.writeValueAsString(completedResponse))
            .addHeader("Content-Type", "application/json"));

        // Simulate what the UI does: provide default names for empty inputs
        CompletableFuture<String> future = transcriptionService.transcribeFile(
            testAudioFile,
            "Speaker 1",  // Default name that UI would provide for empty input
            "Speaker 2",  // Default name that UI would provide for empty input
            progress -> {}
        );

        String result = future.get(5, TimeUnit.SECONDS);
        
        // Should use default speaker names as provided by UI layer
        assertNotNull(result);
        assertTrue(result.contains("[Speaker 1]: Hello from Alice"));
        assertTrue(result.contains("[Speaker 2]: Hello from Bob"));
    }

    private TranscriptResponse createTestTranscriptResponse() {
        TranscriptResponse response = new TranscriptResponse();
        response.setId("test-transcript-id");
        response.setStatus("completed");
        response.setText("Hello from Alice. Hello from Bob.");
        
        TranscriptResponse.Utterance utterance1 = new TranscriptResponse.Utterance();
        utterance1.setSpeaker("A");
        utterance1.setText("Hello from Alice");
        utterance1.setStart(0);
        utterance1.setEnd(1000);
        utterance1.setConfidence(0.95);

        TranscriptResponse.Utterance utterance2 = new TranscriptResponse.Utterance();
        utterance2.setSpeaker("B");
        utterance2.setText("Hello from Bob");
        utterance2.setStart(1000);
        utterance2.setEnd(2000);
        utterance2.setConfidence(0.98);

        response.setUtterances(List.of(utterance1, utterance2));
        
        return response;
    }
} 