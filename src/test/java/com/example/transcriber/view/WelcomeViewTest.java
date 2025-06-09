package com.example.transcriber.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WelcomeViewTest {

    private WelcomeView welcomeView;

    @BeforeEach
    void setUp() {
        welcomeView = new WelcomeView();
    }

    @Test
    void testWelcomeViewInitialization() {
        assertNotNull(welcomeView);
        assertEquals("400px", welcomeView.getMaxWidth());
        assertEquals("0 auto", welcomeView.getStyle().get("margin"));
    }

    @Test
    void testWelcomeViewContainsTitle() {
        H1 title = findComponentByType(welcomeView, H1.class);
        assertNotNull(title, "Welcome view should contain an H1 title");
        assertEquals("MP3 Transcriber", title.getText());
        assertEquals("var(--lumo-primary-color)", title.getStyle().get("color"));
    }

    @Test
    void testWelcomeViewContainsDescription() {
        Paragraph description = findComponentByType(welcomeView, Paragraph.class);
        assertNotNull(description, "Welcome view should contain a description paragraph");
        assertEquals("Secure MP3 transcription service powered by AssemblyAI", description.getText());
        assertEquals("center", description.getStyle().get("text-align"));
        assertEquals("var(--lumo-secondary-text-color)", description.getStyle().get("color"));
    }

    @Test
    void testWelcomeViewContainsGetStartedButton() {
        Button getStartedButton = findComponentByType(welcomeView, Button.class);
        assertNotNull(getStartedButton, "Welcome view should contain a 'Get Started' button");
        assertEquals("Get Started", getStartedButton.getText());
        assertTrue(getStartedButton.getThemeNames().contains("primary"));
        assertTrue(getStartedButton.getThemeNames().contains("large"));
    }

    @Test
    void testWelcomeViewAlignment() {
        assertEquals(WelcomeView.Alignment.CENTER, welcomeView.getAlignItems());
        assertEquals(WelcomeView.JustifyContentMode.CENTER, welcomeView.getJustifyContentMode());
        assertEquals("100%", welcomeView.getWidth());
        assertEquals("100%", welcomeView.getHeight());
    }

    @SuppressWarnings("unchecked")
    private <T> T findComponentByType(WelcomeView view, Class<T> componentType) {
        return (T) view.getChildren()
                .filter(componentType::isInstance)
                .findFirst()
                .orElse(null);
    }
} 