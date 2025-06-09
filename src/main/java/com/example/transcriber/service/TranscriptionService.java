package com.example.transcriber.service;


import com.example.transcriber.dto.TranscriptResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class TranscriptionService {

    private final String baseUrl;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TranscriptionService(@Value("${assemblyai.api-key}") String apiKey) {
        this(apiKey, "https://api.assemblyai.com/v2");
    }

    // Constructor for testing
    public TranscriptionService(String apiKey, String baseUrl) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Upload file to AssemblyAI and get upload URL
     */
    private String uploadFile(File audioFile) throws IOException {
        RequestBody fileBody = RequestBody.create(audioFile, MediaType.parse("audio/mpeg"));
        
        Request request = new Request.Builder()
                .url(baseUrl + "/upload")
                .header("Authorization", apiKey)
                .post(fileBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("File upload failed: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            // Parse JSON response to get upload URL
            return objectMapper.readTree(responseBody).get("upload_url").asText();
        }
    }

    /**
     * Submit transcription job
     */
    private String submitTranscription(String uploadUrl, boolean speakerLabels, int speakersExpected) throws IOException {
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        
        String jsonBody = String.format("""
            {
                "audio_url": "%s",
                "speaker_labels": %s,
                "speakers_expected": %d
            }
            """, uploadUrl, speakerLabels, speakersExpected);

        RequestBody body = RequestBody.create(jsonBody, JSON);
        
        Request request = new Request.Builder()
                .url(baseUrl + "/transcript")
                .header("Authorization", apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Transcription submission failed: " + response.code() + " " + response.message());
            }
            
            String responseBody = response.body().string();
            TranscriptResponse transcriptResponse = objectMapper.readValue(responseBody, TranscriptResponse.class);
            return transcriptResponse.getId();
        }
    }

    /**
     * Poll for transcription completion
     */
    private TranscriptResponse pollForCompletion(String transcriptId, Consumer<String> progressCallback) throws IOException, InterruptedException {
        Request request = new Request.Builder()
                .url(baseUrl + "/transcript/" + transcriptId)
                .header("Authorization", apiKey)
                .get()
                .build();

        while (true) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to get transcription status: " + response.code() + " " + response.message());
                }
                
                String responseBody = response.body().string();
                TranscriptResponse transcript = objectMapper.readValue(responseBody, TranscriptResponse.class);
                
                String status = transcript.getStatus();
                progressCallback.accept("Status: " + status);
                
                if ("completed".equals(status)) {
                    return transcript;
                } else if ("error".equals(status)) {
                    throw new RuntimeException("Transcription failed: " + transcript.getError());
                }
                
                // Wait before polling again
                Thread.sleep(3000);
            }
        }
    }

    /**
     * Regular transcription using HTTP API
     */
    public CompletableFuture<String> transcribeFile(File audioFile, 
                                                   String speaker1Name, 
                                                   String speaker2Name,
                                                   Consumer<String> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progressCallback.accept("Uploading file...");
                String uploadUrl = uploadFile(audioFile);
                
                progressCallback.accept("Starting transcription...");
                String transcriptId = submitTranscription(uploadUrl, true, 2);
                
                progressCallback.accept("Processing transcription...");
                TranscriptResponse transcript = pollForCompletion(transcriptId, progressCallback);
                
                progressCallback.accept("Formatting transcript...");
                String formattedTranscript = formatTranscriptWithSpeakers(transcript, speaker1Name, speaker2Name);
                
                progressCallback.accept("Transcription complete!");
                return formattedTranscript;
                
            } catch (Exception e) {
                log.error("Error during transcription", e);
                progressCallback.accept("Error: " + e.getMessage());
                throw new RuntimeException("Transcription failed", e);
            }
        });
    }



    /**
     * Format transcript with speaker names
     */
    private String formatTranscriptWithSpeakers(TranscriptResponse transcript, String speaker1Name, String speaker2Name) {
        StringBuilder formatted = new StringBuilder();
        
        if (transcript.getUtterances() != null && !transcript.getUtterances().isEmpty()) {
            transcript.getUtterances().forEach(utterance -> {
                String speakerName = "A".equals(utterance.getSpeaker()) ? speaker1Name : speaker2Name;
                formatted.append(String.format("[%s]: %s\n\n", speakerName, utterance.getText()));
            });
        } else {
            // Fallback to regular transcript if speaker diarization is not available
            formatted.append(transcript.getText() != null ? transcript.getText() : "No transcript available");
        }
        
        return formatted.toString();
    }


} 