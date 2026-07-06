package com.einvoice.pipeline.service;

/**
 * Raised when a CSV upload cannot be mapped to an invoice (missing column,
 * unparsable number/date). A client error — surfaced as HTTP 400.
 */
public class CsvImportException extends RuntimeException {

    public CsvImportException(String message) {
        super(message);
    }
}
