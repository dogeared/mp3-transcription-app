package com.example.transcriber.view;

import com.example.transcriber.service.TranscriptionService;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Route(value = "/transcriber", layout = MainLayout.class)
@PageTitle("MP3 Transcriber")
@PermitAll
public class TranscriberView extends VerticalLayout {

    private final TranscriptionService transcriptionService;
    
    private TextField speaker1Field;
    private TextField speaker2Field;
    private Upload upload;
    private Button transcribeButton;
    private ProgressBar progressBar;
    private Paragraph statusLabel;
    private Button downloadButton;
    private Anchor downloadAnchor;
    
    private String currentTranscript;
    private File uploadedFile;
    private UI currentUI;

    public TranscriberView(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
        this.currentUI = UI.getCurrent();
        
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.START);
        
        createHeader();
        createSpeakerFields();
        createFileUpload();
        createTranscribeButton();
        createProgressIndicator();
        createDownloadSection();
        
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
        speaker1Field.setValue("Tes");
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
        
        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        upload = new Upload(buffer);
        
        upload.setAcceptedFileTypes("audio/mpeg", "audio/mp3", ".mp3", "audio/wav", ".wav");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(500 * 1024 * 1024); // 500MB
        
        upload.addSucceededListener(event -> {
            try {
                // Save uploaded file to temporary location
                uploadedFile = File.createTempFile("upload_", "_" + event.getFileName());
                
                try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
                    buffer.getInputStream(event.getFileName()).transferTo(fos);
                }
                
                Notification.show("File uploaded successfully: " + event.getFileName(), 
                    3000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                
                updateUIState();
                
            } catch (IOException e) {
                log.error("Error saving uploaded file", e);
                Notification.show("Error uploading file: " + e.getMessage(), 
                    5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        
        upload.addFileRejectedListener(event -> {
            Notification.show("File rejected: " + event.getErrorMessage(), 
                5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        });
        
        add(uploadHeader, upload);
    }

    private void createTranscribeButton() {
        transcribeButton = new Button("Start Transcription");
        transcribeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        transcribeButton.addClickShortcut(Key.ENTER);
        
        transcribeButton.addClickListener(event -> startTranscription());
        
        add(transcribeButton);
    }

    private void createProgressIndicator() {
        progressBar = new ProgressBar();
        progressBar.setVisible(false);
        progressBar.setWidth("400px");
        
        statusLabel = new Paragraph("Ready to transcribe");
        statusLabel.getStyle().set("margin", "0");
        
        add(progressBar, statusLabel);
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
        upload.setVisible(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
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
            progressBar.setVisible(false);
            transcribeButton.setEnabled(true);
            upload.setVisible(true);
            
            Notification.show("Transcription failed: " + throwable.getMessage(), 
                5000, Notification.Position.TOP_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } else {
            currentTranscript = transcript;
            statusLabel.setText("Transcription completed successfully!");
            progressBar.setVisible(false);
            
            // Setup download
            setupDownload();
            
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