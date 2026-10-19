package com.menta.physical.application.usecase;

import com.menta.physical.domain.exception.InvalidQrCredentialException;
import com.menta.physical.domain.model.SessionId;
import java.util.UUID;

/**
 * Parses the placeholder {@code qr:<studentId>:<sessionId>:<jti>:<exp>}
 * credential format into its four typed claims (US-PHYSICAL-001 escenario
 * 4). Package-private on purpose: this is a format-validation helper for
 * {@code ProcessPhysicalCheckInUseCaseImpl}, not a port — swapping the
 * placeholder for real HMAC later changes {@code
 * FormatQrCredentialSignatureService} and this class together, never the
 * use case's call site.
 *
 * <p>Only checks the token's <em>shape</em>: the {@code qr:} prefix, exactly
 * four {@code :}-separated segments, and that {@code studentId}/{@code
 * sessionId} parse as UUIDs. It deliberately does NOT compare the parsed
 * {@code sessionId} against the request's path parameter, nor recompute the
 * signature, nor check expiry — those need inputs (the path {@code
 * sessionId}, the original raw string, "now") this parser does not have,
 * so the use case performs them as separate, later steps.</p>
 */
final class QrCredentialParser {

    private static final String PREFIX = "qr:";
    private static final int EXPECTED_SEGMENTS = 4;

    private QrCredentialParser() {
    }

    static ParsedQrCredential parse(String qrCredentials) {
        if (qrCredentials == null || !qrCredentials.startsWith(PREFIX)) {
            throw new InvalidQrCredentialException();
        }
        String[] segments = qrCredentials.substring(PREFIX.length()).split(":", -1);
        if (segments.length != EXPECTED_SEGMENTS) {
            throw new InvalidQrCredentialException();
        }
        try {
            UUID studentId = UUID.fromString(segments[0]);
            SessionId sessionId = SessionId.of(segments[1]);
            String jti = segments[2];
            if (jti.isBlank()) {
                throw new InvalidQrCredentialException();
            }
            long expiresAtEpochSeconds = Long.parseLong(segments[3]);
            return new ParsedQrCredential(studentId, sessionId, jti, expiresAtEpochSeconds);
        } catch (IllegalArgumentException e) {
            throw new InvalidQrCredentialException();
        }
    }

    /** The four typed claims a well-formed {@code qrCredentials} token carries. */
    record ParsedQrCredential(
        UUID studentId, SessionId sessionId, String jti, long expiresAtEpochSeconds
    ) {
    }
}
