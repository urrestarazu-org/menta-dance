package com.menta.physical.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Tells operators that the retired shared check-in secret is still configured (#266, D3).
 *
 * <p>Check-in readers now authenticate against the #44 device registry, so
 * {@value #LEGACY_PROPERTY} has no effect any more. Leaving it set is harmless but misleading: it
 * looks like a live credential. This logs one WARN with the property <em>name</em> only; the value
 * is a secret and is never read, formatted or logged. It never fails the startup.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyCheckInDeviceTokenPropertyWarning {

    static final String LEGACY_PROPERTY = "app.physical.checkin.device-token";

    private final Environment environment;

    /** Logs the retirement warning once the application is ready; silent when the key is unset. */
    @EventListener(ApplicationReadyEvent.class)
    public void warnIfPresent() {
        if (environment.containsProperty(LEGACY_PROPERTY)) {
            log.warn(
                "{} is no longer used: QR check-in readers authenticate against the device "
                    + "registry. Remove it from the environment.",
                LEGACY_PROPERTY
            );
        }
    }
}
