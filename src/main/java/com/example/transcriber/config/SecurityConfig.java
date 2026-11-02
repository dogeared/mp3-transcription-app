package com.example.transcriber.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

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
                .requestMatchers("/api/upload/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/transcriber", true)
            )
            .logout(logout -> logout
                .logoutSuccessHandler(logoutSuccessHandler)
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
} 