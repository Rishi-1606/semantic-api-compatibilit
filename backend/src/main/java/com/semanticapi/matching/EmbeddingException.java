package com.semanticapi.matching;

/**
 * Thrown when an {@link EmbeddingProvider} fails to produce an embedding vector
 * and no recovery is possible.
 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
