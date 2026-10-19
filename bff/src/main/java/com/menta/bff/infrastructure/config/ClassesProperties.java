package com.menta.bff.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the public physical classes pages.
 *
 * <p>Binds to application.yml properties prefixed with {@code menta.classes}.
 * </p>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "menta.classes")
public class ClassesProperties {

    /**
     * Time zone in which session instants (UTC upstream) are shown to visitors.
     */
    @NotNull
    private ZoneId displayZone = ZoneId.of("America/Argentina/Buenos_Aires");
}
