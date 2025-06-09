package com.example.transcriber.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test security configuration that completely replaces the production OAuth2 setup
 * with simple form-based authentication for testing purposes.
 */
@TestConfiguration
@EnableWebSecurity
@Profile("test")
public class TestSecurityConfig {

    @Bean(name = "integrationTestSecurityFilterChain")
    @Primary
    public SecurityFilterChain integrationTestSecurityFilterChain(HttpSecurity http) throws Exception {
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
            .csrf(csrf -> csrf.disable()); // Disable CSRF for easier testing

        return http.build();
    }

    @Bean(name = "integrationTestUserDetailsService")
    @Primary
    public UserDetailsService integrationTestUserDetailsService() {
        UserDetails user = User.builder()
            .username("testuser")
            .password(passwordEncoder().encode("testpass"))
            .roles("USER")
            .build();

        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("admin"))
            .roles("ADMIN", "USER")
            .build();

        return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean(name = "integrationTestPasswordEncoder")
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
} 