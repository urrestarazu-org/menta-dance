package com.menta.virtual.infrastructure.cdn;

import com.menta.virtual.application.port.out.BunnyNetSignatureService;

/**
 * Placeholder {@link BunnyNetSignatureService} implementation for the
 * MVP (US-VIRTUAL-004). Returns {@code <pullZoneHostname>/<videoLibraryId>/<videoId>}
 * with NO signing and NO TTL — the signed URL contract is intact, but
 * the URL itself is technically shareable until the orchestrator wires
 * HMAC or the official Bunny.net SDK in a follow-up.
 *
 * <p><strong>Esta implementación es un placeholder.</strong> Cuando se
 * integre HMAC real o SDK de Bunny.net, sólo esta clase cambia; el
 * contrato del método y el resto del módulo permanecen.</p>
 *
 * <p>The MVP chose this placeholder because issue #50 scopes the
 * integration ticket but the orchestrator's decision was to defer the
 * SDK adoption: the auth side of the contract has to be ready before a
 * real signing scheme is decided.</p>
 *
 * <p><strong>This is the explicit non-local branch</strong> (ADR-0040,
 * issue #129): {@code VirtualConfiguration} registers it under {@code
 * @Profile("!e2e-bunny-net")}, so it is the sole candidate whenever the
 * local/E2E profile is not active — every production-like deployment
 * included. Its counterpart, {@link
 * com.menta.virtual.infrastructure.cdn.local.LocalBunnyNetSignatureService},
 * is a deterministic, credential-free adapter reserved for local
 * development and E2E acceptance testing under {@code e2e-bunny-net}.</p>
 */
public class StringFormatBunnyNetSignatureService implements BunnyNetSignatureService {

    private final BunnyNetProperties properties;

    public StringFormatBunnyNetSignatureService(BunnyNetProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generateSignedUrl(String videoId, long expirationTimeInSeconds) {
        // This is a placeholder. Actual implementation would involve hashing and signing.
        // For now, return a simple URL composed from the configured pull zone and library.
        return String.format(
            "%s/%s/%s",
            properties.getPullZoneHostname(),
            properties.getVideoLibraryId(),
            videoId
        );
    }
}
