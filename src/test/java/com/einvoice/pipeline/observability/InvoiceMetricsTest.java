package com.einvoice.pipeline.observability;

import com.einvoice.pipeline.TestcontainersConfiguration;
import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.model.InvoiceFixtures;
import com.einvoice.pipeline.repository.InvoiceRecordRepository;
import com.einvoice.pipeline.service.InvoiceProcessingService;
import com.einvoice.pipeline.validation.InvoiceValidationException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.RequiredSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Drives the pipeline with several invoices — two valid, one arithmetically
 * invalid — and asserts the Micrometer meters reflect the activity, both in
 * the registry and on the Prometheus scrape endpoint.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
// Spring Boot disables metrics export in tests by default; re-enable it so the
// real Prometheus registry and its /actuator/prometheus endpoint are exercised.
@AutoConfigureObservability
class InvoiceMetricsTest {

    @Autowired
    private InvoiceProcessingService processingService;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private InvoiceRecordRepository repository;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    @Test
    void countersAndTimerReflectProcessingActivity() {
        double generatedBefore = counter("einvoice.invoices.generated");
        long timedBefore = timerCount();

        // Two valid invoices...
        processingService.process(withNumber("FA-METRIC-1"));
        processingService.process(withNumber("FA-METRIC-2"));

        // ...and one that violates BR-CO-15 (grand total inconsistent)
        Throwable thrown = catchThrowable(() -> processingService.process(incoherent("FA-METRIC-3")));
        assertThat(thrown).isInstanceOf(InvoiceValidationException.class);

        assertThat(counter("einvoice.invoices.generated") - generatedBefore).isEqualTo(2.0);
        assertThat(rejectedByReason("BR-CO-15")).isGreaterThanOrEqualTo(1.0);
        // Every submission is timed, accepted or rejected: three more samples.
        assertThat(timerCount() - timedBefore).isEqualTo(3L);
    }

    @Test
    void prometheusEndpointExposesTheBusinessMeters() {
        processingService.process(withNumber("FA-PROM-1"));

        ResponseEntity<String> scrape = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(scrape.getStatusCode().is2xxSuccessful())
                .as("prometheus endpoint status=%s", scrape.getStatusCode())
                .isTrue();
        assertThat(scrape.getBody())
                .contains("einvoice_invoices_generated_total")
                .contains("einvoice_processing_duration_seconds")
                .contains("application=\"einvoice-pipeline\"");
    }

    private double counter(String name) {
        return registry.find(name).counter() == null ? 0.0 : registry.find(name).counter().count();
    }

    private double rejectedByReason(String reason) {
        return registry.find("einvoice.invoices.rejected").tag("reason", reason).counter().count();
    }

    private long timerCount() {
        RequiredSearch search = registry.get("einvoice.processing.duration");
        return search.timer().count();
    }

    private static Invoice withNumber(String number) {
        Invoice base = InvoiceFixtures.validInvoice();
        return new Invoice(number, base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), base.lines(),
                base.totalWithoutVat(), base.totalVat(), base.totalWithVat());
    }

    private static Invoice incoherent(String number) {
        Invoice base = InvoiceFixtures.validInvoice();
        return new Invoice(number, base.issueDate(), base.dueDate(), base.currencyCode(),
                base.seller(), base.buyer(), base.lines(),
                base.totalWithoutVat(), base.totalVat(), new BigDecimal("999.00"));
    }
}
