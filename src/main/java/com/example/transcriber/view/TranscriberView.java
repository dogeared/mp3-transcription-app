package com.example.transcriber.view;

import com.example.transcriber.service.ChunkUploadService;
import com.example.transcriber.service.TranscriptionService;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Input;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Route(value = "/transcriber", layout = MainLayout.class)
@PageTitle("MP3 Transcriber")
@PermitAll
@JsModule("./chunked-upload.js")
public class TranscriberView extends VerticalLayout {

    private final TranscriptionService transcriptionService;
    private final ChunkUploadService chunkUploadService;

    private TextField speaker1Field;
    private TextField speaker2Field;
    private Input fileInput;
    private Button selectFileButton;
    private Paragraph fileNameLabel;
    private Button transcribeButton;
    private ProgressBar uploadProgressBar;
    private ProgressBar transcriptionProgressBar;
    private Paragraph statusLabel;
    private Button downloadButton;
    private Anchor downloadAnchor;
    private Button cancelButton;

    private String currentTranscript;
    private String currentUploadId;
    private File uploadedFile;
    private UI currentUI;
    private String currentUserId;

    public TranscriberView(TranscriptionService transcriptionService, ChunkUploadService chunkUploadService) {
        this.transcriptionService = transcriptionService;
        this.chunkUploadService = chunkUploadService;
        this.currentUI = UI.getCurrent();

        // Get user ID for upload service
        try {
            OidcUser user = (OidcUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            this.currentUserId = user.getSubject();
        } catch (Exception e) {
            log.warn("Could not get user ID", e);
            this.currentUserId = "anonymous";
        }

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);

        createHeader();
        createSpeakerFields();
        createFileUpload();
        createTranscribeButton();
        createProgressIndicator();
        createDownloadSection();

        initializeJavaScript();
        updateUIState();
    }

    private void createHeader() {
        H1 title = new H1("MP3 Transcriber");
        title.getStyle().set("margin-bottom", "0");

        // Get user info from security context
        String userName = "User";
        try {
            OidcUser user = (OidcUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            userName = user.getFullName() != null ? user.getFullName() : user.getEmail();
        } catch (Exception e) {
            log.warn("Could not get user information", e);
        }

        Paragraph welcome = new Paragraph("Welcome, " + userName + "!");
        welcome.getStyle().set("margin-top", "0").set("color", "var(--lumo-secondary-text-color)");

        add(title, welcome);
    }

    private void createSpeakerFields() {
        H3 speakerHeader = new H3("Speaker Names");

        speaker1Field = new TextField("Speaker 1 Name");
        speaker1Field.setPlaceholder("Enter name for first speaker");
        speaker1Field.setWidth("300px");

        speaker2Field = new TextField("Speaker 2 Name");
        speaker2Field.setPlaceholder("Enter name for second speaker");
        speaker2Field.setValue("");
        speaker2Field.setWidth("300px");

        HorizontalLayout speakerLayout = new HorizontalLayout(speaker1Field, speaker2Field);
        speakerLayout.setDefaultVerticalComponentAlignment(Alignment.END);

        add(speakerHeader, speakerLayout);
    }

    private void createFileUpload() {
        H3 uploadHeader = new H3("Upload MP3 File");

        // Create hidden file input
        fileInput = new Input();
        fileInput.setType("file");
        fileInput.getElement().setAttribute("accept", "audio/mpeg,audio/mp3,.mp3,audio/wav,.wav");
        fileInput.getStyle()
            .set("display", "none");
        fileInput.setId("chunked-file-input");

        // Create styled button that triggers file input
        selectFileButton = new Button("Select Audio File");
        selectFileButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        selectFileButton.addClickListener(e -> {
            // Trigger file input click via JavaScript
            fileInput.getElement().executeJs("this.click()");
        });

        // Label to show selected file name
        fileNameLabel = new Paragraph("No file selected");
        fileNameLabel.getStyle()
            .set("color", "var(--lumo-secondary-text-color)")
            .set("margin", "0");

        // Cancel button (hidden by default)
        cancelButton = new Button("Cancel Upload");
        cancelButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        cancelButton.setVisible(false);
        cancelButton.addClickListener(e -> cancelUpload());

        // Layout for file selection
        HorizontalLayout fileSelectLayout = new HorizontalLayout(selectFileButton, fileNameLabel, cancelButton);
        fileSelectLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        fileSelectLayout.getStyle().set("gap", "var(--lumo-space-m)");

        // Upload progress bar (hidden initially)
        uploadProgressBar = new ProgressBar();
        uploadProgressBar.setVisible(false);
        uploadProgressBar.setWidth("400px");
        uploadProgressBar.setMin(0);
        uploadProgressBar.setMax(100);

        // Container for file upload section
        Div uploadContainer = new Div(fileInput, fileSelectLayout, uploadProgressBar);
        uploadContainer.setId("upload-container");

        add(uploadHeader, uploadContainer);
    }

    private void createTranscribeButton() {
        transcribeButton = new Button("Start Transcription");
        transcribeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        transcribeButton.addClickShortcut(Key.ENTER);

        transcribeButton.addClickListener(event -> startTranscription());

        add(transcribeButton);
    }

    private void createProgressIndicator() {
        transcriptionProgressBar = new ProgressBar();
        transcriptionProgressBar.setVisible(false);
        transcriptionProgressBar.setWidth("400px");

        statusLabel = new Paragraph("Ready to transcribe");
        statusLabel.getStyle().set("margin", "0");

        add(transcriptionProgressBar, statusLabel);
    }

    private void createDownloadSection() {
        downloadButton = new Button("Download Transcript");
        downloadButton.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        downloadButton.setVisible(false);

        downloadAnchor = new Anchor();
        downloadAnchor.getElement().setAttribute("download", true);
        downloadAnchor.add(downloadButton);

        add(downloadAnchor);
    }

    private void initializeJavaScript() {
        // Initialize the chunked uploader when the page loads
        getElement().executeJs(
            """
            const fileInput = document.getElementById('chunked-file-input');
            if (fileInput) {
                fileInput.addEventListener('change', (event) => {
                    const file = event.target.files[0];
                    if (file) {
                        // Create uploader instance
                        const uploader = new window.ChunkedUploader({
                            serverElement: $0
                        });

                        // Store uploader reference for cancel functionality
                        $0._uploader = uploader;

                        // Start upload
                        uploader.upload(file).catch(error => {
                            console.error('Upload failed:', error);
                        });

                        // Notify server of file selection
                        $0.$server.onFileSelected(file.name, file.size);
                    }
                });
            }
            """,
            getElement()
        );
    }

    /**
     * Called from JavaScript when a file is selected
     */
    @ClientCallable
    public void onFileSelected(String fileName, double fileSize) {
        fileNameLabel.setText(String.format("%s (%.1f MB)", fileName, fileSize / (1024 * 1024)));
        selectFileButton.setEnabled(false);
        cancelButton.setVisible(true);
        uploadProgressBar.setVisible(true);
        uploadProgressBar.setValue(0);
        statusLabel.setText("Uploading...");

        // Reset any previous upload state
        uploadedFile = null;
        currentUploadId = null;
        currentTranscript = null;
        downloadButton.setVisible(false);

        updateUIState();
    }

    /**
     * Called from JavaScript to report upload progress
     */
    @ClientCallable
    public void onChunkProgress(int completed, int total) {
        if (currentUI != null) {
            currentUI.access(() -> {
                double progress = (double) completed / total * 100;
                uploadProgressBar.setValue(progress);
                statusLabel.setText(String.format("Uploading: %d/%d chunks (%.0f%%)", completed, total, progress));
            });
        }
    }

    /**
     * Called from JavaScript when upload completes successfully
     */
    @ClientCallable
    public void onUploadComplete(String uploadId) {
        if (currentUI != null) {
            currentUI.access(() -> {
                this.currentUploadId = uploadId;
                this.uploadedFile = chunkUploadService.getAssembledFile(uploadId, currentUserId);

                uploadProgressBar.setVisible(false);
                cancelButton.setVisible(false);
                selectFileButton.setEnabled(true);
                statusLabel.setText("File uploaded successfully. Ready to transcribe.");

                Notification.show("File uploaded successfully!",
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

                updateUIState();
            });
        }
    }

    /**
     * Called from JavaScript when upload fails
     */
    @ClientCallable
    public void onUploadError(String error) {
        if (currentUI != null) {
            currentUI.access(() -> {
                uploadProgressBar.setVisible(false);
                cancelButton.setVisible(false);
                selectFileButton.setEnabled(true);
                fileNameLabel.setText("No file selected");
                statusLabel.setText("Upload failed: " + error);

                Notification.show("Upload failed: " + error,
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);

                // Cleanup if we have an uploadId
                if (currentUploadId != null) {
                    chunkUploadService.cleanup(currentUploadId);
                    currentUploadId = null;
                }

                updateUIState();
            });
        }
    }

    private void cancelUpload() {
        // Cancel via JavaScript
        getElement().executeJs(
            """
            if ($0._uploader) {
                $0._uploader.cancel();
                $0._uploader = null;
            }
            """,
            getElement()
        );

        // Reset UI
        uploadProgressBar.setVisible(false);
        cancelButton.setVisible(false);
        selectFileButton.setEnabled(true);
        fileNameLabel.setText("No file selected");
        statusLabel.setText("Upload cancelled");

        // Cleanup on server
        if (currentUploadId != null) {
            chunkUploadService.cleanup(currentUploadId);
            currentUploadId = null;
        }

        // Reset file input
        fileInput.getElement().executeJs("this.value = ''");

        updateUIState();
    }

    private void startTranscription() {
        if (uploadedFile == null || !uploadedFile.exists()) {
            Notification.show("Please upload an MP3 file first",
                3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        String speaker1Name = speaker1Field.getValue().trim();
        String speaker2Name = speaker2Field.getValue().trim();

        if (speaker1Name.isEmpty()) speaker1Name = "Speaker 1";
        if (speaker2Name.isEmpty()) speaker2Name = "Speaker 2";

        // Update UI for transcription state
        transcribeButton.setEnabled(false);
        selectFileButton.setEnabled(false);
        transcriptionProgressBar.setVisible(true);
        transcriptionProgressBar.setIndeterminate(true);
        downloadButton.setVisible(false);

        startRegularTranscription(speaker1Name, speaker2Name);
    }

    private void startRegularTranscription(String speaker1Name, String speaker2Name) {
        // Start regular transcription
        CompletableFuture<String> transcriptionFuture = transcriptionService.transcribeFile(
            uploadedFile,
            speaker1Name,
            speaker2Name,
            this::updateProgress
        );

        transcriptionFuture.whenComplete((transcript, throwable) -> {
            if (currentUI != null) {
                currentUI.access(() -> handleTranscriptionComplete(transcript, throwable));
            }
        });
    }

    private void handleTranscriptionComplete(String transcript, Throwable throwable) {
        if (throwable != null) {
            log.error("Transcription failed", throwable);
            statusLabel.setText("Transcription failed: " + throwable.getMessage());
            transcriptionProgressBar.setVisible(false);
            transcribeButton.setEnabled(true);
            selectFileButton.setEnabled(true);

            Notification.show("Transcription failed: " + throwable.getMessage(),
                5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } else {
            currentTranscript = transcript;
            statusLabel.setText("Transcription completed successfully!");
            transcriptionProgressBar.setVisible(false);

            // Setup download
            setupDownload();

            // Cleanup upload temp files
            if (currentUploadId != null) {
                chunkUploadService.cleanup(currentUploadId);
                currentUploadId = null;
            }

            Notification.show("Transcription completed!",
                3000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        }
    }

    private void updateProgress(String message) {
        if (currentUI != null) {
            currentUI.access(() -> {
                statusLabel.setText(message);
            });
        }
    }

    private void setupDownload() {
        if (currentTranscript != null) {
            String speaker2Name = speaker2Field.getValue().trim();
            if (speaker2Name.isEmpty()) {
                speaker2Name = "Speaker2";
            }
            String filename = speaker2Name + "_transcript.txt";

            StreamResource resource = new StreamResource(
                filename,
                () -> new ByteArrayInputStream(currentTranscript.getBytes(StandardCharsets.UTF_8))
            );

            downloadAnchor.setHref(resource);
            downloadButton.setVisible(true);
        }
    }

    private void updateUIState() {
        boolean fileUploaded = uploadedFile != null && uploadedFile.exists();
        transcribeButton.setEnabled(fileUploaded);
    }
}
