package com.menta.bff.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Form posted by the "Suscribirme" button of a plan on the plans page. */
@Data
public class SubscriptionForm {

    @NotBlank
    @Size(max = 128)
    private String planId;

    @NotBlank
    @Size(max = 128)
    private String idempotencyKey;
}
