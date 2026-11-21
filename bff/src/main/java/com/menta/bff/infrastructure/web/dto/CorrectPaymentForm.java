package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.application.dto.CorrectionDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Form of the correction in the payment detail, {@code POST /gestion/pagos/{id}/corregir} (#370): the outcome
 * imposed on a payment that needs reconciliation, with its reason and evidence, both required and at most 500
 * characters once trimmed. The decision travels as text so an unknown value is one Spanish message instead of a
 * binding failure.
 */
@Getter
@NoArgsConstructor
public class CorrectPaymentForm {

    private static final String DECISION_MESSAGE = "Elegí una decisión";

    @NotNull(message = DECISION_MESSAGE)
    @Pattern(regexp = "APPROVED|REJECTED", message = DECISION_MESSAGE)
    private String decision;

    @NotBlank(message = "Ingresá el motivo de la corrección")
    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    private String reason;

    @NotBlank(message = "Ingresá la evidencia")
    @Size(max = 500, message = "La evidencia no puede superar los 500 caracteres")
    private String evidence;

    /** The validated decision; only meaningful once the form validated. */
    public CorrectionDecision toDecision() {
        return CorrectionDecision.valueOf(decision);
    }

    public void setDecision(String decision) {
        this.decision = decision == null ? null : decision.strip();
    }

    public void setReason(String reason) {
        this.reason = reason == null ? null : reason.strip();
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence == null ? null : evidence.strip();
    }
}
