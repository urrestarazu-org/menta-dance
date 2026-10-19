package com.menta.physical.application.usecase;

import com.menta.physical.domain.model.PhysicalDevice;

/**
 * Builds the human-readable {@code previousValue}/{@code newValue} audit snapshots (#44,
 * US-PHYSICAL-007, design C4). Restricted to non-sensitive fields: status and an 8-hex-char
 * {@code secretHashPrefix} of the 64-char SHA-256 hash -- never the raw secret and never the full
 * hash (the leak guard, design C2/C10, asserts this).
 */
final class PhysicalDeviceAuditSnapshot {

    private static final int PREFIX_LENGTH = 8;

    private PhysicalDeviceAuditSnapshot() {
    }

    /** {@code status=ACTIVE;secretHashPrefix=<8 hex>;expiresAt=<iso-8601 or null>} -- register only. */
    static String registered(PhysicalDevice device) {
        return "status=" + device.getStatus() + ";secretHashPrefix=" + secretHashPrefix(device.getSecretHash())
            + ";expiresAt=" + (device.getExpiresAt() != null ? device.getExpiresAt().toString() : "null");
    }

    /** {@code status=...;secretHashPrefix=<8 hex>} -- rotation and revocation before/after snapshots. */
    static String statusAndHashPrefix(PhysicalDevice device) {
        return "status=" + device.getStatus() + ";secretHashPrefix=" + secretHashPrefix(device.getSecretHash());
    }

    private static String secretHashPrefix(String secretHash) {
        return secretHash.substring(0, PREFIX_LENGTH);
    }
}
