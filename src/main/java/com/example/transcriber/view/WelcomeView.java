package com.example.transcriber.view;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route(value = "")
@PageTitle("Welcome - MP3 Transcriber")
@AnonymousAllowed
public class WelcomeView extends VerticalLayout {

    public WelcomeView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H1 title = new H1("MP3 Transcriber");
        title.getStyle().set("color", "var(--lumo-primary-color)");

        Paragraph description = new Paragraph(
            "Secure MP3 transcription service powered by AssemblyAI"
        );
        description.getStyle()
            .set("text-align", "center")
            .set("color", "var(--lumo-secondary-text-color)");

        Button loginButton = new Button("Get Started");
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        loginButton.addClickListener(e -> {
            getUI().ifPresent(ui -> ui.navigate("transcriber"));
        });

        add(title, description, loginButton);
        setMaxWidth("400px");
        getStyle().set("margin", "0 auto");
    }
} 