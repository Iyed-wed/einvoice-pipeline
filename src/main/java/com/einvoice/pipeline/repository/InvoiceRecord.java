package com.einvoice.pipeline.repository;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.validation.ValidationError;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Audit trail entry for one invoice submission. Only metadata is stored —
 * the Factur-X binary is returned to the caller, not archived here: legal
 * archiving has its own requirements (10+ years, integrity seals) and belongs
 * to a dedicated system, not the generation engine.
 */
@Entity
@Table(name = "invoice_record")
public class InvoiceRecord {

    @Id
    private UUID id;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @Column(name = "seller_name", nullable = false, length = 256)
    private String sellerName;

    @Column(name = "buyer_name", nullable = false, length = 256)
    private String buyerName;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "total_with_vat", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalWithVat;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InvoiceStatus status;

    /** Violated EN 16931 rule identifiers, comma-separated. Null when generated. */
    @Column(name = "rejection_reasons", length = 512)
    private String rejectionReasons;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InvoiceRecord() {
        // JPA only
    }

    private InvoiceRecord(Invoice invoice, InvoiceStatus status, String rejectionReasons) {
        this.id = UUID.randomUUID();
        this.invoiceNumber = invoice.invoiceNumber();
        this.sellerName = invoice.seller().name();
        this.buyerName = invoice.buyer().name();
        this.currencyCode = invoice.currencyCode();
        this.totalWithVat = invoice.totalWithVat();
        this.status = status;
        this.rejectionReasons = rejectionReasons;
        this.createdAt = Instant.now();
    }

    public static InvoiceRecord generated(Invoice invoice) {
        return new InvoiceRecord(invoice, InvoiceStatus.GENERATED, null);
    }

    public static InvoiceRecord rejected(Invoice invoice, List<ValidationError> errors) {
        String reasons = errors.stream()
                .map(ValidationError::rule)
                .distinct()
                .collect(Collectors.joining(","));
        return new InvoiceRecord(invoice, InvoiceStatus.REJECTED, reasons);
    }

    public UUID getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public String getSellerName() {
        return sellerName;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public BigDecimal getTotalWithVat() {
        return totalWithVat;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public String getRejectionReasons() {
        return rejectionReasons;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
