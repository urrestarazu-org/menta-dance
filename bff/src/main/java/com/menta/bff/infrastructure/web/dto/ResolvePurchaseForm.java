package com.menta.bff.infrastructure.web.dto;

import com.menta.bff.application.dto.PurchaseResolution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Form of the resolution of a purchase in exception, {@code POST /gestion/compras-con-excepcion/{id}/resolver}
 * (#370): how it is closed and the note that stays in the audit trail, required and at most 500 characters once
 * trimmed. The resolution travels as text so an unknown value is one Spanish message instead of a binding failure.
 */
@Getter
@NoArgsConstructor
public class ResolvePurchaseForm {

    private static final String RESOLUTION_MESSAGE = "Elegí una resolución";

    @NotNull(message = RESOLUTION_MESSAGE)
    @Pattern(regexp = "REFUNDED|COMPENSATED", message = RESOLUTION_MESSAGE)
    private String resolution;

    @NotBlank(message = "Ingresá la nota de la resolución")
    @Size(max = 500, message = "La nota no puede superar los 500 caracteres")
    private String note;

    /** The validated resolution; only meaningful once the form validated. */
    public PurchaseResolution toResolution() {
        return PurchaseResolution.valueOf(resolution);
    }

    public void setResolution(String resolution) {
        this.resolution = resolution == null ? null : resolution.strip();
    }

    public void setNote(String note) {
        this.note = note == null ? null : note.strip();
    }
}
