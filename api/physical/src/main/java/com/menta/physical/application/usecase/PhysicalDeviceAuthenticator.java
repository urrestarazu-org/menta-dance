package com.menta.physical.application.usecase;

import com.menta.physical.application.dto.DeviceAuthenticationRejection;
import com.menta.physical.application.dto.DeviceAuthenticationRejectionReason;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.DeviceAuthenticationRejectionPort;
import com.menta.physical.application.port.out.DeviceSecretHasher;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.exception.CheckInDeviceRevokedException;
import com.menta.physical.domain.exception.DeviceExpiredException;
import com.menta.physical.domain.exception.InvalidDeviceTokenException;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

/**
 * Authenticates a QR check-in reader against the device registry (#266, gate 1 of the check-in).
 *
 * <p>Each rejection is reported once through {@link DeviceAuthenticationRejectionPort} and then
 * thrown. Unknown id, non-UUID id, absent secret and wrong secret are all
 * {@link InvalidDeviceTokenException}, so a caller cannot tell them apart; {@code REVOKED} and
 * {@code EXPIRED} are only revealed once the secret has been proven. The authenticator never
 * touches Redis.</p>
 */
@RequiredArgsConstructor
public class PhysicalDeviceAuthenticator {

    /**
     * Canonical UUID text. {@link UUID#fromString(String)} is lenient ({@code 1-1-1-1-1} parses),
     * so the shape is checked first; this also bounds the id length before any database access.
     */
    private static final Pattern CANONICAL_UUID =
        Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");

    /**
     * Compared against when the device is unknown, so an unknown id costs the same lookup, hash
     * and comparison as a wrong secret (no existence oracle). Same length as a SHA-256 hex digest.
     */
    private static final String DUMMY_SECRET_HASH = "0".repeat(64);

    private final PhysicalDeviceRepository repository;
    private final DeviceSecretHasher hasher;
    private final Clock clock;
    private final DeviceAuthenticationRejectionPort rejectionPort;

    /**
     * Proves the reader's identity: canonical id, matching secret, then status and expiry.
     *
     * @return the authenticated device's id in its canonical form.
     * @throws InvalidDeviceTokenException unknown or malformed id, absent secret or wrong secret.
     * @throws CheckInDeviceRevokedException the proven device is {@code REVOKED}.
     * @throws DeviceExpiredException the proven {@code ACTIVE} device has reached its expiry.
     */
    public DeviceId authenticate(String rawDeviceId, String rawSecret) {
        UUID submittedId = parseCanonicalUuid(rawDeviceId);
        if (submittedId == null || rawSecret == null) {
            throw rejected(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, submittedId,
                new InvalidDeviceTokenException());
        }
        DeviceId deviceId = DeviceId.of(submittedId);
        Optional<PhysicalDevice> found = repository.findById(deviceId);
        String expectedHash = found.map(PhysicalDevice::getSecretHash).orElse(DUMMY_SECRET_HASH);
        boolean secretMatches = constantTimeEquals(hasher.hash(rawSecret), expectedHash);
        if (found.isEmpty() || !secretMatches) {
            throw rejected(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, submittedId,
                new InvalidDeviceTokenException());
        }
        PhysicalDevice device = found.get();
        if (device.getStatus() == DeviceStatus.REVOKED) {
            throw rejected(DeviceAuthenticationRejectionReason.REVOKED, submittedId,
                new CheckInDeviceRevokedException());
        }
        if (hasReachedExpiry(device)) {
            throw rejected(DeviceAuthenticationRejectionReason.EXPIRED, submittedId,
                new DeviceExpiredException());
        }
        return deviceId;
    }

    private static UUID parseCanonicalUuid(String rawDeviceId) {
        if (rawDeviceId == null || !CANONICAL_UUID.matcher(rawDeviceId).matches()) {
            return null;
        }
        return UUID.fromString(rawDeviceId);
    }

    private static boolean constantTimeEquals(String computedHash, String storedHash) {
        return MessageDigest.isEqual(
            computedHash.getBytes(StandardCharsets.UTF_8),
            storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean hasReachedExpiry(PhysicalDevice device) {
        Instant expiresAt = device.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(clock.now());
    }

    private <E extends RuntimeException> E rejected(
        DeviceAuthenticationRejectionReason reason, UUID deviceId, E exception
    ) {
        rejectionPort.report(new DeviceAuthenticationRejection(reason, deviceId));
        return exception;
    }
}
