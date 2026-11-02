package com.example.transcriber.view;

import com.example.transcriber.service.ChunkUploadService;
import com.example.transcriber.service.TranscriptionService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives TranscriberView through the upload and transcription flow the browser triggers.
 */
class TranscriberViewInteractionTest {

    private static final String USER = "user-1";
    private static final String UPLOAD_ID = "11111111-2222-3333-4444-555555555555";

    @TempDir
    Path tempDir;

    private final TranscriptionService transcriptionService = mock(TranscriptionService.class);
    private final ChunkUploadService chunkUploadService = mock(ChunkUploadService.class);

    private TranscriberView view;

    @BeforeEach
    void setUp() {
        ViewTestSupport.currentUiRunningAccessImmediately();
        ViewTestSupport.signIn(USER, "Ada Lovelace", "ada@example.com");
        view = new TranscriberView(transcriptionService, chunkUploadService);
    }

    @AfterEach
    void tearDown() {
        ViewTestSupport.reset();
    }

    @Test
    void welcomesSignedInUser() {
        assertTrue(ViewTestSupport.descendants(view)
                .anyMatch(c -> c instanceof Paragraph p && "Welcome, Ada Lovelace!".equals(p.getText())));
    }

    @Test
    void fileSelectionShowsUploadInProgress() {
        view.onFileSelected("talk.mp3", 5 * 1024 * 1024);

        assertEquals("talk.mp3 (5.0 MB)", this.<Paragraph>field("fileNameLabel").getText());
        assertFalse(this.<Button>field("selectFileButton").isEnabled());
        assertTrue(this.<Button>field("cancelButton").isVisible());
        assertTrue(this.<ProgressBar>field("uploadProgressBar").isVisible());
        assertEquals("Uploading...", status());
        assertFalse(this.<Button>field("transcribeButton").isEnabled());
    }

    @Test
    void chunkProgressUpdatesProgressBarAndStatus() {
        view.onChunkProgress(1, 4);

        assertEquals(25.0, this.<ProgressBar>field("uploadProgressBar").getValue());
        assertEquals("Uploading: 1/4 chunks (25%)", status());
    }

    @Test
    void completedUploadEnablesTranscription() throws IOException {
        uploadCompletes();

        assertEquals("File uploaded successfully. Ready to transcribe.", status());
        assertTrue(this.<Button>field("transcribeButton").isEnabled());
        assertTrue(this.<Button>field("selectFileButton").isEnabled());
        assertFalse(this.<Button>field("cancelButton").isVisible());
        assertFalse(this.<ProgressBar>field("uploadProgressBar").isVisible());
    }

    @Test
    void uploadErrorResetsUi() {
        view.onFileSelected("talk.mp3", 1024);

        view.onUploadError("network down");

        assertEquals("Upload failed: network down", status());
        assertEquals("No file selected", this.<Paragraph>field("fileNameLabel").getText());
        assertTrue(this.<Button>field("selectFileButton").isEnabled());
        assertFalse(this.<Button>field("cancelButton").isVisible());
    }

    @Test
    void uploadErrorCleansUpKnownUpload() {
        ReflectionTestUtils.setField(view, "currentUploadId", UPLOAD_ID);

        view.onUploadError("network down");

        verify(chunkUploadService).cleanup(UPLOAD_ID);
        assertNull(ReflectionTestUtils.getField(view, "currentUploadId"));
    }

    @Test
    void cancelButtonStopsUploadAndCleansUp() {
        view.onFileSelected("talk.mp3", 1024);
        ReflectionTestUtils.setField(view, "currentUploadId", UPLOAD_ID);

        this.<Button>field("cancelButton").click();

        assertEquals("Upload cancelled", status());
        assertFalse(this.<Button>field("cancelButton").isVisible());
        assertTrue(this.<Button>field("selectFileButton").isEnabled());
        verify(chunkUploadService).cleanup(UPLOAD_ID);
    }

    @Test
    void transcriptionWithoutUploadDoesNothing() {
        ReflectionTestUtils.invokeMethod(view, "startTranscription");

        verify(transcriptionService, never()).transcribeFile(any(), any(), any(), any());
        assertEquals("Ready to transcribe", status());
    }

    @Test
    void successfulTranscriptionOffersDownloadWithDefaultSpeakerNames() throws IOException {
        File audio = uploadCompletes();
        when(transcriptionService.transcribeFile(eq(audio), eq("Speaker 1"), eq("Speaker 2"), any()))
                .thenReturn(CompletableFuture.completedFuture("Speaker 1: Hello"));

        this.<Button>field("transcribeButton").click();

        assertEquals("Transcription completed successfully!", status());
        assertEquals("Speaker 1: Hello", ReflectionTestUtils.getField(view, "currentTranscript"));
        assertTrue(this.<Button>field("downloadButton").isVisible());
        assertFalse(this.<ProgressBar>field("transcriptionProgressBar").isVisible());
        verify(chunkUploadService).cleanup(UPLOAD_ID);
    }

    @Test
    void transcriptionUsesEnteredSpeakerNamesAndReportsProgress() throws IOException {
        File audio = uploadCompletes();
        this.<TextField>field("speaker1Field").setValue(" Ada ");
        this.<TextField>field("speaker2Field").setValue("Grace");
        CompletableFuture<String> pending = new CompletableFuture<>();
        when(transcriptionService.transcribeFile(eq(audio), eq("Ada"), eq("Grace"), any())).thenReturn(pending);

        this.<Button>field("transcribeButton").click();

        assertFalse(this.<Button>field("transcribeButton").isEnabled());
        assertTrue(this.<ProgressBar>field("transcriptionProgressBar").isVisible());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> progress = ArgumentCaptor.forClass(Consumer.class);
        verify(transcriptionService).transcribeFile(eq(audio), eq("Ada"), eq("Grace"), progress.capture());
        progress.getValue().accept("Transcription status: processing");
        assertEquals("Transcription status: processing", status());

        pending.complete("Ada: Hi\nGrace: Hello");
        assertTrue(this.<Button>field("downloadButton").isVisible());
    }

    @Test
    void failedTranscriptionReportsErrorAndAllowsRetry() throws IOException {
        File audio = uploadCompletes();
        when(transcriptionService.transcribeFile(eq(audio), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("quota exceeded")));

        this.<Button>field("transcribeButton").click();

        assertEquals("Transcription failed: quota exceeded", status());
        assertTrue(this.<Button>field("transcribeButton").isEnabled());
        assertTrue(this.<Button>field("selectFileButton").isEnabled());
        assertFalse(this.<Button>field("downloadButton").isVisible());
        verify(chunkUploadService, never()).cleanup(any());
    }

    private File uploadCompletes() throws IOException {
        File audio = Files.writeString(tempDir.resolve("talk.mp3"), "audio").toFile();
        when(chunkUploadService.getAssembledFile(UPLOAD_ID, USER)).thenReturn(audio);
        view.onUploadComplete(UPLOAD_ID);
        return audio;
    }

    private String status() {
        return this.<Paragraph>field("statusLabel").getText();
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) {
        return (T) ReflectionTestUtils.getField(view, name);
    }
}
