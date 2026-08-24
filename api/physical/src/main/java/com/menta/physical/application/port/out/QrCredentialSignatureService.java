package com.menta.physical.application.port.out;

/**
 * Port that turns a (studentId, sessionId, jti, expiresAtEpochSeconds)
 * tuple into the opaque string the door reader expects. The placeholder
 * implementation in {@code infrastructure.qr.FormatQrCredentialSignatureService}
 * just concatenates the inputs. A follow-up replaces that class with a
 * proper signed token — an HMAC over the claims plus the expiration
 * timestamp, the same signed-URL/signed-token pattern used by presigned
 * S3 URLs or Cloudflare signed URLs — so a tampered or forged credential
 * fails the signature check instead of merely looking well-formed; the
 * port and use cases are unchanged.
 *
 * <p>The four inputs map 1:1 to the four {@code :}-separated segments of
 * {@code qr:<studentId>:<sessionId>:<jti>:<expiresAtEpochSeconds>} so the
 * door-side verifier can parse them back out for short-circuit checks
 * (expiry, claim match) before reaching the database.</p>
 */
public interface QrCredentialSignatureService {

    /**
     * @param studentId the acting student's UUID — must match the JWT
     *                  principal used to issue the token.
     * @param sessionId the target session's UUID.
     * @param jti       a unique-per-issuance token id (UUID is acceptable,
     *                  but any opaque string works).
     * @param expiresAtEpochSeconds Unix epoch seconds after which the
     *                              door reader must reject the token.
     * @return the opaque credential string the QR payload carries.
     */
    String sign(String studentId, String sessionId, String jti, long expiresAtEpochSeconds);
}
