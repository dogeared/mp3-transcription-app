package com.example.transcriber.config;

import com.example.transcriber.controller.ChunkUploadController;
import com.example.transcriber.service.ChunkUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the production SecurityConfig: the chunked upload API requires a CSRF token
 * (which the browser uploader fetches from /api/upload/csrf), while GET /logout keeps working.
 */
@WebMvcTest
@ContextConfiguration(classes = {SecurityConfig.class, ChunkUploadController.class})
// The OAuth2 client registration is mocked, so skip the auto-configuration that would build it
@TestPropertySource(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration,"
        + "org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration,"
        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
class SecurityConfigCsrfTest {

    private static final String INIT_BODY = "{\"fileName\":\"a.mp3\",\"fileSize\":1,\"totalChunks\":1}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChunkUploadService chunkUploadService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void uploadApiRejectsRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/upload/init")
                        .with(oidcLogin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INIT_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadApiAcceptsRequestWithCsrfToken() throws Exception {
        mockMvc.perform(post("/api/upload/init")
                        .with(oidcLogin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INIT_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void tokenFromCsrfEndpointIsAcceptedByUploadApi() throws Exception {
        // Same flow as frontend/chunked-upload.js: fetch the token, then send it as a header
        MockHttpSession session = new MockHttpSession();
        String body = mockMvc.perform(get("/api/upload/csrf").with(oidcLogin()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        CsrfResponse csrf = new JsonMapper().readValue(body, CsrfResponse.class);

        mockMvc.perform(post("/api/upload/init")
                        .with(oidcLogin())
                        .session(session)
                        .header(csrf.headerName, csrf.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INIT_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void uploadApiRejectsCrossSiteDeleteWithoutCsrfToken() throws Exception {
        mockMvc.perform(delete("/api/upload/00000000-0000-0000-0000-000000000000")
                        .with(oidcLogin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getLogoutStillLogsOut() throws Exception {
        mockMvc.perform(get("/logout").with(oidcLogin()))
                .andExpect(status().is3xxRedirection());
    }

    record CsrfResponse(@JsonProperty("headerName") String headerName, @JsonProperty("token") String token) {
    }
}
