package com.einvoice.pipeline.validation;

import java.util.List;

/**
 * Carries the full list of business-rule violations for an invoice, so the
 * client can fix everything in one round trip instead of one error at a time.
 */
public class InvoiceValidationException extends RuntimeException {

    private final transient List<ValidationError> errors;

    public InvoiceValidationException(String invoiceNumber, List<ValidationError> errors) {
        super("Invoice " + invoiceNumber + " violates " + errors.size() + " EN 16931 business rule(s)");
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}
