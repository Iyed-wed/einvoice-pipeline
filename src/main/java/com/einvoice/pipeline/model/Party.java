package com.einvoice.pipeline.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * A trading party (seller BG-4 or buyer BG-7 in EN 16931).
 */
public record Party(

        @NotBlank(message = "Party name is mandatory (EN 16931 BT-27 / BT-44)")
        String name,

        /**
         * VAT identifier (BT-31 / BT-48), e.g. FR12345678901 or TN1234567.
         * Optional at the structural level; business rules may require it
         * depending on the invoice type.
         */
        @Pattern(regexp = "[A-Z]{2}[A-Za-z0-9]{2,13}",
                message = "VAT identifier must start with a 2-letter country prefix, e.g. FR12345678901")
        String vatId,

        @NotNull(message = "Party postal address is mandatory (EN 16931 BG-5 / BG-8)")
        @Valid
        Address address
) {
}
