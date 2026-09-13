package com.semanticapi.schema;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a schema string cannot be parsed.
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SchemaParseException extends RuntimeException {

    public SchemaParseException(String message) {
        super(message);
    }

    public SchemaParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
