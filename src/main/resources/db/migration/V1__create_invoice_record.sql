-- Audit trail of processed invoices: one row per submission, GENERATED or REJECTED.
CREATE TABLE invoice_record (
    id                UUID PRIMARY KEY,
    invoice_number    VARCHAR(64)  NOT NULL,
    seller_name       VARCHAR(256) NOT NULL,
    buyer_name        VARCHAR(256) NOT NULL,
    currency_code     VARCHAR(3)   NOT NULL,
    total_with_vat    NUMERIC(19, 2) NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    rejection_reasons VARCHAR(512),
    created_at        TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_invoice_record_number ON invoice_record (invoice_number);
CREATE INDEX idx_invoice_record_created_at ON invoice_record (created_at);
