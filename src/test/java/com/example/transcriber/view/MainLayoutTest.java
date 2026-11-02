package com.example.transcriber.view;

import com.example.transcriber.service.TranscriptionService;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.example.transcriber.view.ViewTestSupport.descendants;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MainLayoutTest {

    private final TranscriptionService transcriptionService = mock(TranscriptionService.class);

    @AfterEach
    void tearDown() {
        ViewTestSupport.reset();
    }

    @Test
    void headerShowsAppTitle() {
        MainLayout layout = new MainLayout(transcriptionService);

        assertTrue(descendants(layout).anyMatch(c -> c instanceof H1 h && "MP3 Transcriber".equals(h.getText())));
    }

    @Test
    void headerShowsUsersFullName() {
        ViewTestSupport.signIn("user-1", "Ada Lovelace", "ada@example.com");

        assertEquals("Logged in as: Ada Lovelace", userInfo(new MainLayout(transcriptionService)));
    }

    @Test
    void headerFallsBackToEmailWithoutFullName() {
        ViewTestSupport.signIn("user-1", null, "ada@example.com");

        assertEquals("Logged in as: ada@example.com", userInfo(new MainLayout(transcriptionService)));
    }

    @Test
    void headerShowsDefaultNameWhenNotSignedIn() {
        assertEquals("Logged in as: User", userInfo(new MainLayout(transcriptionService)));
    }

    private static String userInfo(MainLayout layout) {
        return descendants(layout)
                .filter(c -> c instanceof Span s && s.getText().startsWith("Logged in as:"))
                .map(c -> ((Span) c).getText())
                .findFirst()
                .orElseThrow();
    }
}
