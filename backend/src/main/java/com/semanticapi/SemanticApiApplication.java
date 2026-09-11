package com.semanticapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Semantic API Compatibility & Automatic Transformation Framework.
 *
 * <p>This Spring Boot application manages:
 * <ul>
 *   <li>API registration and version management</li>
 *   <li>Endpoint and schema management</li>
 *   <li>NLP/AI orchestration for semantic analysis</li>
 *   <li>Transformation rule generation and validation</li>
 *   <li>Database access via Spring Data JPA</li>
 * </ul>
 *
 * <p>Run locally with H2 (default) or PostgreSQL (docker profile).
 */
@SpringBootApplication
public class SemanticApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SemanticApiApplication.class, args);
    }
}
