package com.example.transcriber.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        // Ends the Keycloak SSO session too, then returns to the app root
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
            new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/");

        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/vaadin/**", "/VAADIN/**").permitAll()
                .requestMatchers("/images/**", "/icons/**", "/static/**", "/webjars/**").permitAll()
                // The container health check calls this without a session; it only reports UP/DOWN
                // (health details stay hidden by default)
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/api/upload/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/transcriber", true)
            )
            .logout(logout -> logout
                // Keep GET /logout working (the logout button navigates to it); with CSRF
                // enabled Spring would otherwise only accept POST
                .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/logout"))
                .logoutSuccessHandler(logoutSuccessHandler)
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
            )
            // Vaadin protects its own requests with its own CSRF token, so Spring's check is
            // applied to the state-changing calls of the chunked upload REST API
            .csrf(csrf -> csrf
                .requireCsrfProtectionMatcher(new AndRequestMatcher(
                    CsrfFilter.DEFAULT_CSRF_MATCHER,
                    PathPatternRequestMatcher.withDefaults().matcher("/api/upload/**")
                ))
            );

        return http.build();
    }
} 