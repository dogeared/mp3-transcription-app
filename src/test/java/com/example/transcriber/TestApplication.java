package com.example.transcriber;

import com.example.transcriber.config.TestSecurityConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Test-specific application class that excludes the production SecurityConfig
 * to avoid OAuth2 configuration conflicts during testing.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = "com.example.transcriber",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.example.transcriber.config.SecurityConfig.class
    )
)
@Import(TestSecurityConfig.class)
@Profile("test")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
} 