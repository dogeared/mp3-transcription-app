package com.example.transcriber.view;

import com.example.transcriber.service.TranscriptionService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class MainLayout extends AppLayout {

    private final TranscriptionService transcriptionService;

    public MainLayout(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        H1 logo = new H1("MP3 Transcriber");
        logo.addClassNames(
            LumoUtility.FontSize.LARGE,
            LumoUtility.Margin.MEDIUM
        );

        // Get user info
        String userName = "User";
        try {
            OidcUser user = (OidcUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            userName = user.getFullName() != null ? user.getFullName() : user.getEmail();
        } catch (Exception e) {
            // Ignore - will use default
        }

        Span userInfo = new Span("Logged in as: " + userName);
        userInfo.addClassNames(LumoUtility.FontSize.SMALL);

        Button logoutButton = new Button("Logout");
        logoutButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        logoutButton.addClickListener(e -> {
            getUI().ifPresent(ui -> ui.getPage().setLocation("/logout"));
        });

        HorizontalLayout userSection = new HorizontalLayout(userInfo, logoutButton);
        userSection.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        userSection.addClassNames(LumoUtility.Gap.MEDIUM);

        HorizontalLayout header = new HorizontalLayout(
            new DrawerToggle(), 
            logo,
            userSection
        );
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(logo);
        header.setWidthFull();
        header.addClassNames(
            LumoUtility.Padding.Vertical.NONE,
            LumoUtility.Padding.Horizontal.MEDIUM
        );

        addToNavbar(header);
    }

    private void createDrawer() {
        VerticalLayout drawerContent = new VerticalLayout();
        
        // Add navigation items here if needed in the future
        Span menuTitle = new Span("Navigation");
        menuTitle.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);
        
        Button transcriberButton = new Button("Transcriber");
        transcriberButton.addClickListener(e -> {
            getUI().ifPresent(ui -> ui.navigate(TranscriberView.class));
        });
        
        drawerContent.add(menuTitle, transcriberButton);
        drawerContent.setPadding(true);
        drawerContent.setSpacing(true);
        
        addToDrawer(drawerContent);
    }
} 