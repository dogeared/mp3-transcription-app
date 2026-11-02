package com.example.transcriber.controller;

import com.example.transcriber.config.SecurityConfig;
import com.example.transcriber.dto.ChunkUploadInitRequest;
import com.example.transcriber.dto.ChunkUploadInitResponse;
import com.example.transcriber.dto.ChunkUploadStatus;
import com.example.transcriber.service.ChunkUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class, ChunkUploadController.class})
// The OAuth2 client registration is mocked, so skip the auto-configuration that would build it
@TestPropertySource(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,"
        + "org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration,"
        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
class ChunkUploadControllerTest {

    private static final String USER = "user-1";
    private static final String UPLOAD_ID = "11111111-2222-3333-4444-555555555555";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChunkUploadService chunkUploadService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void csrfEndpointReturnsHeaderNameAndToken() throws Exception {
        mockMvc.perform(get("/api/upload/csrf").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void initUploadPassesRequestAndUserToService() throws Exception {
        when(chunkUploadService.initializeUpload(any(), eq(USER)))
                .thenReturn(new ChunkUploadInitResponse(UPLOAD_ID, 1024L));

        mockMvc.perform(post("/api/upload/init").with(user()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"talk.mp3\",\"totalSize\":2048,\"totalChunks\":2,\"mimeType\":\"audio/mpeg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(UPLOAD_ID))
                .andExpect(jsonPath("$.chunkSize").value(1024));

        verify(chunkUploadService).initializeUpload(argThat((ChunkUploadInitRequest r) ->
                r.getFileName().equals("talk.mp3") && r.getTotalChunks() == 2), eq(USER));
    }

    @Test
    void uploadChunkStoresChunk() throws Exception {
        mockMvc.perform(chunkRequest(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.chunkIndex").value(3));

        verify(chunkUploadService).receiveChunk(eq(UPLOAD_ID), eq(3), any(MultipartFile.class), eq(USER));
    }

    @Test
    void uploadChunkReturnsBadRequestForInvalidChunk() throws Exception {
        doThrow(new IllegalArgumentException("Invalid chunk index: 9"))
                .when(chunkUploadService).receiveChunk(eq(UPLOAD_ID), anyInt(), any(), eq(USER));

        mockMvc.perform(chunkRequest(9))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Invalid chunk index: 9"));
    }

    @Test
    void uploadChunkReturnsServerErrorWhenStorageFails() throws Exception {
        doThrow(new IOException("disk full"))
                .when(chunkUploadService).receiveChunk(eq(UPLOAD_ID), anyInt(), any(), eq(USER));

        mockMvc.perform(chunkRequest(0))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to store chunk: disk full"));
    }

    @Test
    void completeUploadReturnsStatus() throws Exception {
        when(chunkUploadService.assembleFile(UPLOAD_ID, USER)).thenReturn(new File("talk.mp3"));
        when(chunkUploadService.getStatus(UPLOAD_ID, USER))
                .thenReturn(new ChunkUploadStatus(UPLOAD_ID, Set.of(0, 1), 2, "COMPLETE", null, "talk.mp3"));

        mockMvc.perform(completeRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETE"))
                .andExpect(jsonPath("$.fileName").value("talk.mp3"));
    }

    @Test
    void completeUploadReturnsBadRequestForUnknownUpload() throws Exception {
        when(chunkUploadService.assembleFile(UPLOAD_ID, USER))
                .thenThrow(new IllegalArgumentException("Upload session not found"));

        mockMvc.perform(completeRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"))
                .andExpect(jsonPath("$.error").value("Upload session not found"));
    }

    @Test
    void completeUploadReturnsBadRequestWhenChunksAreMissing() throws Exception {
        when(chunkUploadService.assembleFile(UPLOAD_ID, USER))
                .thenThrow(new IllegalStateException("Missing chunks: received 1 of 2"));

        mockMvc.perform(completeRequest())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Missing chunks: received 1 of 2"));
    }

    @Test
    void completeUploadReturnsServerErrorWhenAssemblyFails() throws Exception {
        when(chunkUploadService.assembleFile(UPLOAD_ID, USER)).thenThrow(new IOException("read error"));

        mockMvc.perform(completeRequest())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.uploadId").value(UPLOAD_ID))
                .andExpect(jsonPath("$.error").value("Assembly failed: read error"));
    }

    @Test
    void statusReturnsUploadStatus() throws Exception {
        when(chunkUploadService.getStatus(UPLOAD_ID, USER))
                .thenReturn(new ChunkUploadStatus(UPLOAD_ID, Set.of(0), 2, "IN_PROGRESS", null, "talk.mp3"));

        mockMvc.perform(get("/api/upload/{id}/status", UPLOAD_ID).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.totalChunks").value(2));
    }

    @Test
    void statusReturnsNotFoundForUnknownUpload() throws Exception {
        mockMvc.perform(get("/api/upload/{id}/status", UPLOAD_ID).with(user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelUploadDelegatesToService() throws Exception {
        mockMvc.perform(delete("/api/upload/{id}", UPLOAD_ID).with(user()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.uploadId").value(UPLOAD_ID));

        verify(chunkUploadService).cancelUpload(UPLOAD_ID, USER);
    }

    private static RequestPostProcessor user() {
        return oidcLogin().idToken(token -> token.subject(USER));
    }

    private static RequestBuilder chunkRequest(int index) {
        return multipart("/api/upload/{id}/chunk/{index}", UPLOAD_ID, index)
                .file(new MockMultipartFile("chunk", "blob", "application/octet-stream", new byte[]{1, 2, 3}))
                .with(user())
                .with(csrf());
    }

    private static RequestBuilder completeRequest() {
        return post("/api/upload/{id}/complete", UPLOAD_ID).with(user()).with(csrf());
    }
}
