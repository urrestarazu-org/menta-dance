package com.menta.bff.infrastructure.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form backing the {@code /reenviar-activacion} page.
 *
 * <p>Same email rules as the Auth API's {@code ResendActivationRequest}; the API stays
 * authoritative and its 400 is mapped to a generic form error.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
public class ResendActivationForm {

    @NotBlank(message = "Ingresá tu email")
    @Email(message = "El formato del email es inválido")
    private String email;

    /**
     * Stores the email without surrounding whitespace, which a paste often adds.
     *
     * @param email the submitted email, possibly null
     */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
