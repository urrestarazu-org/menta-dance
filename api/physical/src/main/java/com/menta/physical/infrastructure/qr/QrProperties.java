package com.menta.physical.infrastructure.qr;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the check-in QR flow (US-PHYSICAL-001). Bound from
 * properties keyed under {@code app.qr}.
 *
 * <p>Mirrors {@code BunnyNetProperties}: every value below is a field
 * default, not an {@code application.yml} entry — there was no explicit
 * guidance in the issue for these numbers, so the defaults chosen are
 * reasonable for an in-person class (a 30-minute early window, a 2-hour
 * late window for classes that run long, a short-lived 60-second
 * credential, and a 10-second lock) rather than values pulled from a
 * requirement. Overriding any of them in a real environment is a plain
 * property, no code change needed.</p>
 */
@Data
@ConfigurationProperties(prefix = "app.qr")
public class QrProperties {

    /** How early before {@code scheduledAt} a check-in is still accepted. */
    private Duration sessionWindowBefore = Duration.ofMinutes(30);

    /** How late after {@code scheduledAt} a check-in is still accepted. */
    private Duration sessionWindowAfter = Duration.ofHours(2);

    /** How long an issued {@code qrCredentials} token remains valid. */
    private Duration credentialTtl = Duration.ofSeconds(60);

    /** TTL of both Redis check-in locks (US-PHYSICAL-001 escenario 6). */
    private Duration lockTtl = Duration.ofSeconds(10);
}
