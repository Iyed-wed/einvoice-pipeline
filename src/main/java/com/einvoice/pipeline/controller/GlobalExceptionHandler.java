package com.einvoice.pipeline.controller;

import com.einvoice.pipeline.service.CsvImportException;
import com.einvoice.pipeline.service.FacturXGenerationException;
import com.einvoice.pipeline.validation.InvoiceValidationException;
import com.einvoice.pipeline.validation.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;

/**
 * Translates every failure into an RFC 7807 problem response
 * (application/problem+json). Clients always get a structured, actionable
 * body — never a raw stack trace.
 *
 * <p>Status code semantics: 400 for structurally malformed requests (missing
 * mandatory fields, wrong formats), 422 for well-formed invoices that violate
 * EN 16931 business rules.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Structural violations detected by Bean Validation on the request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleStructuralErrors(MethodArgumentNotValidException e) {
        List<ValidationError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ValidationError(
                        "STRUCTURE", fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invoice structure is invalid");
        problem.setType(URI.create("urn:einvoice-pipeline:problem:invalid-structure"));
        problem.setProperty("errors", errors);
        return problem;
    }

    /** CSV upload that cannot be mapped to an invoice (missing column, bad value). */
    @ExceptionHandler(CsvImportException.class)
    public ProblemDetail handleCsvImportError(CsvImportException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("CSV import failed");
        problem.setType(URI.create("urn:einvoice-pipeline:problem:invalid-csv"));
        problem.setDetail(e.getMessage());
        return problem;
    }

    /** EN 16931 business rule violations (amount/VAT consistency). */
    @ExceptionHandler(InvoiceValidationException.class)
    public ProblemDetail handleBusinessRuleErrors(InvoiceValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Invoice violates EN 16931 business rules");
        problem.setType(URI.create("urn:einvoice-pipeline:problem:en16931-violation"));
        problem.setDetail(e.getMessage());
        problem.setProperty("errors", e.getErrors());
        return problem;
    }

    /** Infrastructure failures: log the full trace server-side, return a clean message. */
    @ExceptionHandler(FacturXGenerationException.class)
    public ProblemDetail handleGenerationFailure(FacturXGenerationException e) {
        log.error("Factur-X generation failed", e);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Factur-X generation failed");
        problem.setType(URI.create("urn:einvoice-pipeline:problem:generation-failure"));
        problem.setDetail("The invoice could not be generated. The incident has been logged.");
        return problem;
    }
}
