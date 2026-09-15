package com.semanticapi.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring-managed factory that selects and wraps the active {@link EmbeddingProvider}.
 *
 * <p>Provider selection logic:
 * <ol>
 *   <li>If {@code ai.embedding.provider=lexical-fallback} → use {@link LexicalFallbackEmbeddingProvider}</li>
 *   <li>Otherwise construct an {@link OllamaEmbeddingProvider}</li>
 *   <li>On any {@link EmbeddingException}: if {@code ai.embedding.fallback-on-error=true}
 *       → delegate to {@link LexicalFallbackEmbeddingProvider} and log a warning;
 *       otherwise rethrow</li>
 * </ol>
 *
 * <p>The wrapping is transparent — callers use {@link EmbeddingProvider} and never
 * know which implementation is active.
 */
@Component
public class EmbeddingProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingProviderFactory.class);

    private final EmbeddingConfig config;
    private final EmbeddingProvider primary;
    private final LexicalFallbackEmbeddingProvider fallback;

    public EmbeddingProviderFactory(EmbeddingConfig config) {
        this.config   = config;
        this.fallback = new LexicalFallbackEmbeddingProvider();

        if ("lexical-fallback".equalsIgnoreCase(config.getProvider())) {
            this.primary = fallback;
            log.info("EmbeddingProviderFactory: using LexicalFallback (configured explicitly)");
        } else {
            this.primary = new OllamaEmbeddingProvider(
                    config.getBaseUrl(), config.getModel(), config.getTimeoutSeconds());
            log.info("EmbeddingProviderFactory: primary=Ollama [{}] @ {}",
                    config.getModel(), config.getBaseUrl());
        }
    }

    /**
     * Returns an {@link EmbeddingProvider} that honours the fallback-on-error policy.
     * The returned provider's {@link EmbeddingProvider#embed(String)} method will
     * transparently delegate to the fallback when the primary fails (if configured).
     */
    public EmbeddingProvider getProvider() {
        if (!config.isFallbackOnError() || primary == fallback) {
            return primary;
        }
        // Wrap with auto-fallback behaviour
        return new EmbeddingProvider() {
            @Override
            public List<Float> embed(String text) {
                try {
                    return primary.embed(text);
                } catch (EmbeddingException e) {
                    log.warn("Primary embedding provider failed ({}), falling back to lexical: {}",
                            primary.providerName(), e.getMessage());
                    return fallback.embed(text);
                }
            }

            @Override
            public String providerName() {
                // Detect whether we're actually using the fallback by testing connectivity
                // The first call will determine this; use primary name optimistically.
                return primary.providerName();
            }

            @Override
            public String modelName() { return primary.modelName(); }
        };
    }

    /** Returns the raw primary provider (no fallback wrapper). Used for health/status checks. */
    public EmbeddingProvider getPrimaryProvider() { return primary; }

    /** Returns the fallback provider directly. */
    public EmbeddingProvider getFallbackProvider() { return fallback; }

    /** Current configuration snapshot. */
    public EmbeddingConfig getConfig() { return config; }
}
