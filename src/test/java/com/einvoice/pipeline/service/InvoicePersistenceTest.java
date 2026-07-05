package com.einvoice.pipeline.service;

import com.einvoice.pipeline.TestcontainersConfiguration;
import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.model.InvoiceFixtures;
import com.einvoice.pipeline.repository.InvoiceRecord;
import com.einvoice.pipeline.repository.InvoiceRecordRepository;
import com.einvoice.pipeline.repository.InvoiceStatus;
import com.einvoice.pipeline.validation.InvoiceValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers integration test against a real PostgreSQL: every processed
 * invoice — accepted or rejected — leaves a re-readable audit trail row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class InvoicePersistenceTest {

    @Autowired
    private InvoiceProcessingService processingService;

    @Autowired
    private InvoiceRecordRepository repository;

    @BeforeEach
    void cleanAuditTrail() {
        repository.deleteAll();
    }

    @Test
    void generatedInvoiceIsPersistedAndReadable() {
        Instant before = Instant.now();

        processingService.process(InvoiceFixtures.validInvoice());

        List<InvoiceRecord> records = repository.findByInvoiceNumber("FA-2026-0001");
        assertThat(records).hasSize(1);

        InvoiceRecord record = records.getFirst();
        assertThat(record.getStatus()).isEqualTo(InvoiceStatus.GENERATED);
        assertThat(record.getSellerName()).isEqualTo("ACME Services SARL");
        assertThat(record.getBuyerName()).isEqualTo("Client Corp SA");
        assertThat(record.getCurrencyCode()).isEqualTo("EUR");
        assertThat(record.getTotalWithVat()).isEqualByComparingTo("300.00");
        assertThat(record.getRejectionReasons()).isNull();
        assertThat(record.getCreatedAt()).isAfterOrEqualTo(before.minusSeconds(1));

        // Re-read through the primary key as well, not only the query method
        assertThat(repository.findById(record.getId())).isPresent();
    }

    @Test
    void rejectedInvoiceLeavesAnAuditTrailWithReasons() {
        Invoice base = InvoiceFixtures.validInvoice();
        Invoice incoherent = new Invoice(base.invoiceNumber(), base.issueDate(), base.dueDate(),
                base.currencyCode(), base.seller(), base.buyer(), base.lines(),
                base.totalWithoutVat(), new BigDecimal("37.00"), new BigDecimal("310.00"));

        assertThatThrownBy(() -> processingService.process(incoherent))
                .isInstanceOf(InvoiceValidationException.class);

        List<InvoiceRecord> records = repository.findByInvoiceNumber("FA-2026-0001");
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().getStatus()).isEqualTo(InvoiceStatus.REJECTED);
        assertThat(records.getFirst().getRejectionReasons()).contains("BR-CO-14").contains("BR-CO-15");
    }
}
