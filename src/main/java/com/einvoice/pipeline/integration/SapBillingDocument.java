package com.einvoice.pipeline.integration;

import java.time.LocalDate;
import java.util.List;

/**
 * A minimal projection of an SAP SD billing document (VBRK header / VBRP items),
 * using SAP field semantics. This is the shape an OData or BAPI/RFC call would
 * return; here it is a plain carrier so the mapping to the canonical model can
 * be demonstrated and tested without a live SAP system.
 */
public record SapBillingDocument(
        String billingDocument,       // VBRK-VBELN
        LocalDate billingDocumentDate,// VBRK-FKDAT
        String transactionCurrency,   // VBRK-WAERK (ISO 4217)
        SapParty soldToParty,         // payer/customer
        SapParty companyParty,        // issuing company code / seller
        List<SapBillingItem> items) {

    /** A trading party as returned by SAP business-partner services. */
    public record SapParty(
            String name,          // NAME1
            String vatNumber,     // STCEG
            String street,        // STRAS
            String postalCode,    // PSTLZ
            String city,          // ORT01
            String country) {     // LAND1 (ISO 3166-1 alpha-2)
    }

    /** A billing item (VBRP). */
    public record SapBillingItem(
            String material,          // description / ARKTX
            java.math.BigDecimal billedQuantity,  // FKIMG
            java.math.BigDecimal netPrice,        // NETWR / quantity
            java.math.BigDecimal taxRate) {       // MWSKZ resolved to a percentage
    }
}
