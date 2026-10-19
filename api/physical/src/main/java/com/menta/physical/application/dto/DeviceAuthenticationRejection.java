package com.menta.physical.application.dto;

import java.util.UUID;

/**
 * A rejected QR check-in device authentication (#266). Carries opaque data only: never the secret
 * and never raw non-UUID input.
 *
 * @param reason why the authentication was rejected.
 * @param deviceId the submitted device id when it is a valid UUID, otherwise {@code null}. Never a
 *     metric tag.
 */
public record DeviceAuthenticationRejection(
    DeviceAuthenticationRejectionReason reason,
    UUID deviceId
) {
}
