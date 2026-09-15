package com.semanticapi.matching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link EmbeddingProvider} that calls a local <a href="https://ollama.com">Ollama</a> server.
 *
 * <h2>API called</h2>
 * {@code POST {baseUrl}/api/embeddings}
 * <pre>
 * Request:  {"model":"nomic-embed-text","prompt":"phone number"}
 * Response: {"embedding":[0.12, -0.34, ...]}
 * </pre>
 *
 * <h2>Configuration</h2>
 * <pre>
 * ai.embedding.provider:   ollama
 * ai.embedding.base-url:   http://localhost:11434
 * ai.embedding.model:      nomic-embed-text
 * ai.embedding.timeout-seconds: 10
 * </pre>
 *
 * <h2>Setup</h2>
 * <pre>
 * # Install Ollama, then pull the model:
 * ollama pull nomic-embed-text
 * </pre>
 *
 * <p>If Ollama is unreachable and {@code ai.embedding.fallback-on-error=true},
 * the {@link EmbeddingProviderFactory} will delegate to the
 * {@link LexicalFallbackEmbeddingProvider} instead.
 */
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;

    public OllamaEmbeddingProvider(String baseUrl, String model, int timeoutSeconds) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");   // strip trailing slash
        this.model   = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("prompt", text);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new EmbeddingException("Ollama returned HTTP " + response.statusCode()
                        + " for model=" + model + ": " + response.body());
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode embeddingNode = root.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new EmbeddingException("Ollama response missing 'embedding' field: " + response.body());
            }

            List<Float> vector = new ArrayList<>(embeddingNode.size());
            for (JsonNode val : embeddingNode) {
                vector.add(val.floatValue());
            }

            log.debug("Ollama embed [{}] → {} dimensions for '{}'", model, vector.size(), text);
            return vector;

        } catch (EmbeddingException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new EmbeddingException(
                    "Failed to reach Ollama at " + baseUrl + " — is Ollama running? " + e.getMessage(), e);
        }
    }

    @Override
    public String providerName() { return "ollama"; }

    @Override
    public String modelName() { return model; }
}
