package com.menta.virtual.infrastructure.cdn;

import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real {@link BunnyNetSignatureService}: signs the MP4 file URL of a Bunny.net Stream video with the library's
 * CDN token authentication (advanced scheme, HMAC-SHA256), issue #751.
 *
 * <p>URL shape: {@code https://<pullZoneHostname>/<videoId>/<fileName>?token=<token>&expires=<epochSeconds>},
 * with {@code fileName} defaulting to {@code play_720p.mp4} (needs "MP4 Fallback" on the library). The video
 * library id is not part of the URL.</p>
 *
 * <p>Token, per Bunny.net's documentation and its reference implementations
 * ({@code BunnyWay/BunnyCDN.TokenAuthentication}):
 * {@code "HS256-" + base64url_no_padding(HMAC-SHA256(securityKey, signaturePath + expires))}, where
 * {@code signaturePath} is the URL-DECODED path ({@code /<videoId>/<fileName>}) and {@code expires} the decimal
 * epoch seconds. No IP binding, country rules or {@code token_path} are used, so the token covers exactly this
 * path and there are no other query parameters to sign.</p>
 *
 * <p>The key is the Stream library's Security → General page, card "Token authentication key" (labelled "API key"
 * there; not the API key of the library's API page, not the read-only key). It never needs to be reset: doing so
 * breaks embeds and CDN tokens alike. "CDN token authentication" must be enabled on that same page. Bunny.net
 * documents the scheme at /cdn/security/token-authentication (advanced HS256 vs basic).</p>
 *
 * <p><strong>Secret handling:</strong> the security key is only fed to the HMAC; it is never part of the URL,
 * a log line or an exception message. Each signed URL is logged at INFO as {@code videoId}, host, file name and
 * {@code expiresAt} only: never the URL, the query string or the token. The remaining TTL is not logged because
 * it would need "now", which belongs to the module's {@code Clock} port (ADR-0048), not to this adapter.</p>
 *
 * <p>This is the non-local branch of ADR-0040 (as amended on 2026-11-14): selected by
 * {@code VirtualConfiguration} everywhere except under {@code e2e-bunny-net}, and under {@code demo} only when a
 * security key is configured.</p>
 */
public final class TokenAuthBunnyNetSignatureService implements BunnyNetSignatureService {

    private static final Logger LOG = LoggerFactory.getLogger(TokenAuthBunnyNetSignatureService.class);

    private static final String TOKEN_PREFIX = "HS256-";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final BunnyNetProperties properties;

    public TokenAuthBunnyNetSignatureService(BunnyNetProperties properties) {
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code videoId} is blank or contains {@code /}, or
     *     {@code expirationTimeInSeconds} is not positive.
     * @throws IllegalStateException if the security key is not configured.
     */
    @Override
    public String generateSignedUrl(String videoId, long expirationTimeInSeconds) {
        if (videoId == null || videoId.isBlank() || videoId.contains("/")) {
            LOG.warn("Bunny.net URL signing rejected: videoId is blank or not a single path segment");
            throw new IllegalArgumentException("videoId must be a non-blank single path segment");
        }
        if (expirationTimeInSeconds <= 0) {
            LOG.warn("Bunny.net URL signing rejected: expirationTimeInSeconds is not a positive epoch-second instant");
            throw new IllegalArgumentException("expirationTimeInSeconds must be a positive epoch-second instant");
        }
        String securityKey = properties.getSecurityKey();
        if (securityKey == null || securityKey.isBlank()) {
            LOG.warn("Bunny.net URL signing rejected: app.cdn.bunny-net.security-key is not configured");
            throw new IllegalStateException("app.cdn.bunny-net.security-key is not configured");
        }
        String fileName = properties.getFileName();
        String signedPath = "/" + videoId + "/" + fileName;
        LOG.info("Bunny.net signed URL: videoId={} host={} file={} expiresAt={}",
            videoId, properties.pullZoneHost(), fileName, isoInstant(expirationTimeInSeconds));
        return String.format(
            Locale.ROOT,
            "%s/%s/%s?token=%s&expires=%d",
            properties.pullZoneBaseUrl(),
            encodeSegment(videoId),
            encodeSegment(fileName),
            token(securityKey, signedPath, expirationTimeInSeconds),
            expirationTimeInSeconds
        );
    }

    /**
     * Token for the plain (no IP, no country, no {@code token_path}) case. Package-private so the unit test can
     * pin it against the vector published by Bunny.net.
     *
     * @param signedPath URL-decoded path of the file, e.g. {@code /300kb.jpg}
     */
    static String token(String securityKey, String signedPath, long expiresEpochSeconds) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(securityKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((signedPath + expiresEpochSeconds).getBytes(StandardCharsets.UTF_8));
            return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            // HmacSHA256 is a mandatory JCA algorithm; an invalid key is impossible for a non-empty secret.
            // Do not chain the cause: nothing derived from the key may reach logs or responses.
            throw new IllegalStateException("HMAC-SHA256 signing is unavailable");
        }
    }

    /** ISO-8601 UTC rendering of the expiry; logging must never break signing, even for an absurd expiry. */
    private static String isoInstant(long epochSeconds) {
        try {
            return Instant.ofEpochSecond(epochSeconds).toString();
        } catch (DateTimeException outOfRange) {
            return "out-of-range(" + epochSeconds + ")";
        }
    }

    private static String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
