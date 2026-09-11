package com.semanticapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — verifies the Spring context loads successfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class SemanticApiApplicationTests {

    @Test
    void contextLoads() {
        // Passes if the application context starts without errors
    }
}
