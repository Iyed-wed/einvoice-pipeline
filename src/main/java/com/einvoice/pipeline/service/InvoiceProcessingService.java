package com.einvoice.pipeline.service;

import com.einvoice.pipeline.model.Invoice;
import com.einvoice.pipeline.repository.InvoiceRecord;
import com.einvoice.pipeline.repository.InvoiceRecordRepository;
import com.einvoice.pipeline.validation.En16931Validator;
import com.einvoice.pipeline.validation.InvoiceValidationException;
import com.einvoice.pipeline.validation.ValidationError;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pipeline entry point: validates, generates, and records every submission.
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

    public InvoiceProcessingService(En16931Validator validator,
                                    FacturXGenerationService generationService,
                                    InvoiceRecordRepository repository) {
        this.validator = validator;
        this.generationService = generationService;
        this.repository = repository;
    }

    /**
     * @return the Factur-X file for a conformant invoice
     * @throws InvoiceValidationException if EN 16931 business rules are violated;
     *                                    the rejection is recorded before throwing
     */
    public byte[] process(Invoice invoice) {
        List<ValidationError> errors = validator.validate(invoice);
        if (!errors.isEmpty()) {
            repository.save(InvoiceRecord.rejected(invoice, errors));
            throw new InvoiceValidationException(invoice.invoiceNumber(), errors);
        }

        byte[] facturX = generationService.generateFacturX(invoice);
        repository.save(InvoiceRecord.generated(invoice));
        return facturX;
    }
}
