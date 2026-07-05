package com.einvoice.pipeline.repository;

/** Outcome of an invoice submission, recorded in the audit trail. */
public enum InvoiceStatus {
    GENERATED,
    REJECTED
}
