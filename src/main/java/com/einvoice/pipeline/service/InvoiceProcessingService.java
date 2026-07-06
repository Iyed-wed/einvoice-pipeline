package com.einvoice.pipeline.service;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.observability.InvoiceMetrics;
import com.einvoice.pipeline.repository.InvoiceRecord;
import com.einvoice.pipeline.repository.InvoiceRecordRepository;
import com.einvoice.pipeline.validation.En16931Validator;
import com.einvoice.pipeline.validation.InvoiceValidationException;
import com.einvoice.pipeline.validation.ValidationError;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pipeline entry point: validates, generates, records, and measures every
 * submission.
 *
 * <p>Rejections are persisted too — a compliance audit trail must show what
 * was refused and why, not only what succeeded. Each save runs in its own
 * transaction on purpose: the REJECTED row must survive the exception thrown
 * back to the client.</p>
 */
@Service
public class InvoiceProcessingService {

    private final En16931Validator validator;
    private final FacturXGenerationService generationService;
    private final InvoiceRecordRepository repository;
    private final InvoiceMetrics metrics;

    public InvoiceProcessingService(En16931Validator validator,
                                    FacturXGenerationService generationService,
                                    InvoiceRecordRepository repository,
                                    InvoiceMetrics metrics) {
        this.validator = validator;
        this.generationService = generationService;
        this.repository = repository;
        this.metrics = metrics;
    }

    /**
     * @return the Factur-X file for a conformant invoice
     * @throws InvoiceValidationException if EN 16931 business rules are violated;
     *                                    the rejection is recorded before throwing
     */
    public byte[] process(Invoice invoice) {
        Timer.Sample sample = metrics.startSample();
        try {
            List<ValidationError> errors = validator.validate(invoice);
            if (!errors.isEmpty()) {
                repository.save(InvoiceRecord.rejected(invoice, errors));
                errors.stream()
                        .map(ValidationError::rule)
                        .distinct()
                        .forEach(metrics::recordRejected);
                throw new InvoiceValidationException(invoice.invoiceNumber(), errors);
            }

            byte[] facturX = generationService.generateFacturX(invoice);
            repository.save(InvoiceRecord.generated(invoice));
            metrics.recordGenerated();
            return facturX;
        } finally {
            // Timed even on rejection: latency of refused invoices matters too.
            metrics.stopSample(sample);
        }
    }
}
