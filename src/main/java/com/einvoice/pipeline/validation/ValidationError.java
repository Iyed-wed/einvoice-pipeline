package com.einvoice.pipeline.validation;

/**
 * One violation of an EN 16931 business rule, expressed in the vocabulary of
 * the standard so the client can trace it back to the official rule list.
 *
 * @param rule    the EN 16931 business rule identifier, e.g. "BR-CO-15"
 * @param field   the invoice field the violation is about
 * @param message a human-readable explanation including the expected value
 */
public record ValidationError(String rule, String field, String message) {
}
