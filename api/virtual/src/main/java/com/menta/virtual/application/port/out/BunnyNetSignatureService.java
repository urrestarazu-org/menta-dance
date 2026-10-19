package com.menta.virtual.application.port.out;

/**
 * Cross-module port for signing a Bunny.net {@code videoId} URL
 * (US-VIRTUAL-004). Virtual owns the call site; the implementation
 * lives in {@code infrastructure} and is free to depend on whatever
 * SDK arrives in that layer later.
 *
 * <p>The contract is intentionally narrow: one method, two inputs, one
 * string. The implementation chooses how (and whether) to apply HMAC,
 * tokens, or TTLs — the rest of the application layer never cares.</p>
 *
 * <p>Keeping the seam at this interface means the orchestrator can swap
 * the placeholder implementation for an HMAC-backed implementation
 * (or the official Bunny.net SDK, once one is accepted onto the
 * dependency tree) without touching
 * {@code GetPublicLessonStreamUseCaseImpl}, the controller, or the wire
 * DTO. ADR-0040 is the architectural note behind that decision.</p>
 */
public interface BunnyNetSignatureService {

    /**
     * Build a signed URL for {@code videoId} that expires at the given
     * epoch-second instant.
     *
     * @param videoId the Bunny.net video identifier stored on
     *     {@code virtual_lessons.video_id} — opaque to this module,
     *     just a key for the CDN.
     * @param expirationTimeInSeconds epoch-second at which the URL stops
     *     serving. Forwarded as-is by the use case, which sets it to
     *     {@code now + 4h} for every stream issued by this MVP.
     * @return an absolute URL the client can hand to a player. The
     *     string shape is the implementation's responsibility.
     */
    String generateSignedUrl(String videoId, long expirationTimeInSeconds);
}
