package com.menta.bff.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Form posted by the "Pagar con Mercado Pago" button of the quote page.
 *
 * <p>{@code courseId} only picks the way back to the class page after a failure; it is
 * encoded as a path segment before it is used and never reaches the upstream.
 * </p>
 */
@Data
public class PhysicalPurchaseForm {

    @NotBlank
    @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private String quoteId;

    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;

    private String courseId;
}
