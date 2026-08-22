package com.menta.virtual.infrastructure.cdn;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the Bunny.net pull zone + video library used by the
 * lesson-stream pipeline (US-VIRTUAL-004). Bound from properties keyed
 * under {@code app.cdn.bunny-net}; both fields are mandatory and
 * fail-fast at startup if blank.
 *
 * <p>Why a dedicated properties class and not a constructor @Value pair?
 * Spring Boot's binding + validation surface gives operators a single
 * place to misconfigure, with one consistent error rather than a
 * NullPointerException deep inside the {@link BunnyNetSignatureService}.
 * Validated at startup, never at request time.</p>
 *
 * <p>Defaults are intentionally absent: this is production configuration,
 * not dev filler. Tests that need a property value use
 * {@code @TestPropertySource} so the real boot path stays strict.</p>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.cdn.bunny-net")
public class BunnyNetProperties {

    /**
     * Hostname of the Bunny.net pull zone (e.g. {@code vz-abc123.b-cdn.net}).
     * Combined with {@link #videoLibraryId} + the per-lesson {@code videoId}
     * by {@link BunnyNetSignatureService}.
     */
    @NotBlank(message = "app.cdn.bunny-net.pullZoneHostname must be configured")
    private String pullZoneHostname;

    /**
     * Bunny.net video-library id (e.g. {@code 12345}). Combined with
     * {@link #pullZoneHostname} + the per-lesson {@code videoId} by
     * {@link BunnyNetSignatureService}.
     */
    @NotBlank(message = "app.cdn.bunny-net.videoLibraryId must be configured")
    private String videoLibraryId;
}
