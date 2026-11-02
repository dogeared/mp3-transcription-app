package com.example.transcriber.integration;

import com.example.transcriber.TranscriberApplication;
import com.example.transcriber.service.ChunkUploadService;
import com.example.transcriber.service.TranscriptionService;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Starts the real application, with the production SecurityConfig, against a stub OIDC provider
 * that serves Keycloak-style discovery metadata. Covers the login redirect (authorization code + PKCE),
 * protection of the app's pages and the CSRF check on the upload API.
 */
@SpringBootTest(classes = TranscriberApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TranscriberApplicationIntegrationTest {

    private static final MockWebServer oidcProvider = startOidcProvider();

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    private int port;

    @Autowired
    private TranscriptionService transcriptionService;

    @Autowired
    private ChunkUploadService chunkUploadService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        registry.add("KEYCLOAK_ISSUER_URI", TranscriberApplicationIntegrationTest::issuer);
        registry.add("KEYCLOAK_CLIENT_ID", () -> "mp3-transcriber");
        registry.add("assemblyai.api-key", () -> "test-api-key");
        String uploads = Files.createTempDirectory("mp3-transcriber-it").toString();
        registry.add("upload.temp.directory", () -> uploads);
    }

    @AfterAll
    static void stopOidcProvider() throws IOException {
        oidcProvider.shutdown();
    }

    @Test
    void contextLoadsWithServices() {
        assertNotNull(transcriptionService);
        assertNotNull(chunkUploadService);
    }

    @Test
    void loginRedirectUsesAuthorizationCodeWithPkce() throws Exception {
        HttpResponse<String> response = get("/oauth2/authorization/keycloak");

        assertEquals(302, response.statusCode());
        String location = response.headers().firstValue("Location").orElseThrow();
        assertTrue(location.startsWith(issuer() + "/protocol/openid-connect/auth?"), location);
        assertTrue(location.contains("response_type=code"), location);
        assertTrue(location.contains("client_id=mp3-transcriber"), location);
        assertTrue(location.contains("code_challenge="), location);
        assertTrue(location.contains("code_challenge_method=S256"), location);
        assertTrue(location.contains("redirect_uri=http://localhost:" + port + "/login/oauth2/code/keycloak"), location);
        assertFalse(location.contains("client_secret"), location);
    }

    @Test
    void transcriberPageRequiresLogin() throws Exception {
        HttpResponse<String> response = get("/transcriber");

        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow()
                .endsWith("/oauth2/authorization/keycloak"));
    }

    @Test
    void uploadApiRejectsAnonymousRequests() throws Exception {
        HttpResponse<String> response = get("/api/upload/csrf");

        assertEquals(302, response.statusCode());
    }

    @Test
    void anonymousUploadIsSentToLogin() throws Exception {
        // Signed-in requests without a CSRF token are covered by SecurityConfigCsrfTest
        HttpRequest request = HttpRequest.newBuilder(url("/api/upload/init"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"fileName\":\"a.mp3\",\"totalChunks\":1}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(302, response.statusCode());
        assertTrue(response.headers().firstValue("Location").orElseThrow()
                .endsWith("/oauth2/authorization/keycloak"), response.headers().firstValue("Location").orElse(""));
    }

    @Test
    void welcomePageRequiresLogin() throws Exception {
        HttpResponse<String> response = get("/");

        assertEquals(302, response.statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(url(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String issuer() {
        String url = oidcProvider.url("/realms/test").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static MockWebServer startOidcProvider() {
        MockWebServer server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (!"/realms/test/.well-known/openid-configuration".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(404);
                }
                String issuer = issuer();
                String discovery = """
                        {"issuer":"%1$s",
                         "authorization_endpoint":"%1$s/protocol/openid-connect/auth",
                         "token_endpoint":"%1$s/protocol/openid-connect/token",
                         "jwks_uri":"%1$s/protocol/openid-connect/certs",
                         "userinfo_endpoint":"%1$s/protocol/openid-connect/userinfo",
                         "end_session_endpoint":"%1$s/protocol/openid-connect/logout",
                         "response_types_supported":["code"],
                         "subject_types_supported":["public"],
                         "id_token_signing_alg_values_supported":["RS256"],
                         "code_challenge_methods_supported":["S256"]}
                        """.formatted(issuer);
                return new MockResponse().setHeader("Content-Type", "application/json").setBody(discovery);
            }
        });
        try {
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException("Could not start stub OIDC provider", e);
        }
        return server;
    }
}
