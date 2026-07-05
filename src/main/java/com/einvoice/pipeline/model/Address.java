package com.einvoice.pipeline.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Postal address (EN 16931 BG-5 seller / BG-8 buyer). Only the country code
 * is structurally mandatory per the standard (BT-40 / BT-55); street, city
 * and postal code are expected in practice but not enforced here.
 */
public record Address(

        String street,

        String postalCode,

        String city,

        @NotNull(message = "Country code is mandatory (EN 16931 BT-40 / BT-55)")
        @Pattern(regexp = "[A-Z]{2}", message = "Country must be an ISO 3166-1 alpha-2 code, e.g. FR or TN")
        String countryCode
) {
}
