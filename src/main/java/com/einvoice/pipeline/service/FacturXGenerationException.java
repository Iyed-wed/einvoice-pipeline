package com.einvoice.pipeline.service;

/**
 * Raised when the Factur-X export fails for a structurally valid invoice —
 * an infrastructure problem (PDF/A conversion, XML embedding), not a client error.
 */
public class FacturXGenerationException extends RuntimeException {

    public FacturXGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
