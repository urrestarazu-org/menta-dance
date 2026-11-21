package com.menta.bff.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Form of the rejection in the payment detail, {@code POST /gestion/pagos/{id}/rechazar} (#370): the reason the API
 * mails to the buyer. It is required and, like in the use case, at most 500 characters once trimmed.
 */
@Getter
@NoArgsConstructor
public class RejectPaymentForm {

    @NotBlank(message = "Ingresá el motivo del rechazo")
    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String reason;

    /** Stores the reason without surrounding whitespace. */
    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.strip();
    }
}
