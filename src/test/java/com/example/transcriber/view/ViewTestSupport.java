package com.example.transcriber.view;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

/**
 * Helpers for view tests: a UI that runs {@code access()} commands immediately, a signed-in OIDC
 * user, and component tree lookups.
 */
final class ViewTestSupport {

    private ViewTestSupport() {
    }

    /** A real UI, set as current, whose {@code access()} runs the command on the calling thread. */
    static UI currentUiRunningAccessImmediately() {
        UI ui = spy(new UI());
        doAnswer(invocation -> {
            invocation.<Command>getArgument(0).execute();
            return null;
        }).when(ui).access(any(Command.class));
        UI.setCurrent(ui);
        return ui;
    }

    /** Signs in an OIDC user; {@code name} or {@code email} may be null. */
    static void signIn(String subject, String name, String email) {
        OidcIdToken.Builder token = OidcIdToken.withTokenValue("id-token").subject(subject);
        if (name != null) {
            token.claim("name", name);
        }
        if (email != null) {
            token.claim("email", email);
        }
        DefaultOidcUser user = new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), token.build());
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "keycloak"));
    }

    static void reset() {
        SecurityContextHolder.clearContext();
        UI.setCurrent(null);
    }

    static Stream<Component> descendants(Component root) {
        return root.getChildren().flatMap(child -> Stream.concat(Stream.of(child), descendants(child)));
    }
}
