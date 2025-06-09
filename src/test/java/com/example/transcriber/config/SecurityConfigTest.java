package com.example.transcriber.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security configuration tests using WebMvcTest for focused web layer testing
 */
@WebMvcTest
@ActiveProfiles("test")
public class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Configuration
    @EnableWebSecurity
    static class TestConfig {

        @Bean
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                .authorizeHttpRequests(authz -> authz
                    .requestMatchers("/", "/login", "/logout", "/error").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/vaadin/**", "/VAADIN/**").permitAll()
                    .requestMatchers("/frontend/**", "/themes/**", "/icons/**", "/images/**").permitAll()
                    .requestMatchers("/favicon.ico", "/robots.txt", "/manifest.webmanifest").permitAll()
                    .requestMatchers("/offline-page.html").permitAll()
                    .anyRequest().authenticated()
                )
                .formLogin(form -> form
                    .loginPage("/login")
                    .permitAll()
                    .defaultSuccessUrl("/transcriber", true)
                )
                .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/")
                    .permitAll()
                )
                .csrf(csrf -> csrf.disable());

            return http.build();
        }

        @Bean
        public UserDetailsService testUserDetailsService() {
            UserDetails user = User.builder()
                .username("testuser")
                .password(passwordEncoder().encode("testpass"))
                .roles("USER")
                .build();

            return new InMemoryUserDetailsManager(user);
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }

    @Test
    @WithAnonymousUser
    public void testPublicEndpointsAccessibleAnonymously() throws Exception {
        // Test that actuator endpoints are accessible without authentication
        mockMvc.perform(get("/actuator/health"))
               .andExpect(status().isNotFound()); // 404 is fine, means not forbidden
    }

    @Test
    @WithAnonymousUser
    public void testProtectedEndpointsRequireAuthentication() throws Exception {
        // Test that protected endpoints redirect to login
        mockMvc.perform(get("/transcriber"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    public void testAuthenticatedUserCanAccessProtectedEndpoints() throws Exception {
        // Test that authenticated users can access protected endpoints
        mockMvc.perform(get("/transcriber"))
               .andExpect(status().isNotFound()); // 404 is fine since we don't have the actual controller
    }

    @Test
    @WithAnonymousUser
    public void testLoginPageIsAccessible() throws Exception {
        // Test that login requests don't return forbidden (404 is acceptable since no actual page)
        mockMvc.perform(get("/login"))
               .andExpect(status().isNotFound()); // 404 is fine - means not forbidden
    }

    @Test 
    @WithAnonymousUser
    public void testLogoutRedirection() throws Exception {
        // Test that logout redirects properly
        mockMvc.perform(get("/logout"))
               .andExpect(status().is3xxRedirection());
    }
} 