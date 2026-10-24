package com.menta.bff.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Contact details shown on the public contact page (#650).
 *
 * <p>Binds to application.yml properties prefixed with {@code menta.contact}. The repository holds no
 * real value: each environment supplies its own through environment variables, and a blank value is
 * simply not shown.
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "menta.contact")
public class ContactProperties {

    private String email = "";
    private String phone = "";
    private String address = "";

    /** Whether an email address is configured. */
    public boolean hasEmail() {
        return !email.isBlank();
    }

    /** Whether a phone number is configured. */
    public boolean hasPhone() {
        return !phone.isBlank();
    }

    /** Whether a postal address is configured. */
    public boolean hasAddress() {
        return !address.isBlank();
    }

    /** Whether no contact detail is configured at all. */
    public boolean isEmpty() {
        return !hasEmail() && !hasPhone() && !hasAddress();
    }

    /**
     * The phone number reduced to what a {@code tel:} link accepts: digits and a leading plus.
     */
    public String phoneHref() {
        return phone.replaceAll("[^0-9+]", "");
    }
}
