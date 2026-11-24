package com.menta.virtual.infrastructure.cdn;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Bunny.net pull zone used by the lesson-stream pipeline (US-VIRTUAL-004, #751). Bound from
 * properties keyed under {@code app.cdn.bunny-net}; the environment-variable form of each property is the
 * upper-cased name without separators (for example {@code APP_CDN_BUNNYNET_SECURITYKEY}).
 *
 * <p>The defaults for {@link #pullZoneHostname} and {@link #videoLibraryId} are deliberate placeholders flagged
 * with {@code invalid-missing-config} / {@code missing-config}. They let a context boot under the {@code demo}
 * and {@code e2e-bunny-net} profiles, which use the local adapter and need no Bunny.net configuration. Whenever
 * the real {@link TokenAuthBunnyNetSignatureService} is selected,
 * {@link com.menta.virtual.infrastructure.config.VirtualConfiguration} calls {@link #requireRealCdnConfiguration()}
 * and the boot fails if a placeholder or the missing security key is still in place.</p>
 *
 * <p>{@link #securityKey} is a secret: it is excluded from {@link #toString()} and never appears in any
 * validation message. Where to copy it from: see the Javadoc of {@link #securityKey}.</p>
 */
@Data
@ConfigurationProperties(prefix = "app.cdn.bunny-net")
public class BunnyNetProperties {

    /** Marker contained in every placeholder default (and in the production compose fallback). */
    static final String PLACEHOLDER_MARKER = "missing-config";

    /** Base URL of Bunny Stream's management API. */
    static final String DEFAULT_API_BASE_URL = "https://video.bunnycdn.com";

    /**
     * Hostname of the Bunny.net pull zone, with or without the {@code https://} scheme and a trailing slash
     * (e.g. {@code vz-abc123.b-cdn.net}). The signed URL is always built as {@code https://<hostname>/...}; see
     * {@link #pullZoneBaseUrl()}.
     */
    private String pullZoneHostname = "https://invalid-missing-config.local";

    /**
     * Bunny.net Stream video-library id (e.g. {@code 12345}). It is NOT part of the Stream file URL
     * ({@code https://<pull-zone>/<videoId>/<fileName>}); it is kept as required configuration for the local
     * adapter's URL shape and for features that address the library (embeds, API), which are out of scope here.
     */
    private String videoLibraryId = "missing-config";

    /**
     * The Stream library's "Token authentication key": the card of that name on the library's Security → General
     * page (labelled "API key" there, but a different value from the library's API key on the API page and from
     * the read-only API key). Bunny.net signs embeds and CDN token authentication URLs with that same key, so it
     * must never be reset. The library also needs "CDN token authentication" enabled on that page. Bunny.net
     * documents the CDN scheme at /cdn/security/token-authentication (advanced HS256 vs basic). Secret: never
     * logged and excluded from {@link #toString()}. Blank by default; the local adapter does not need it.
     */
    @ToString.Exclude
    private String securityKey = "";

    /**
     * The library's API key (header {@code AccessKey}) for the management API at {@link #apiBaseUrl}: the "API key"
     * of the library's API page, NOT the {@link #securityKey} (the CDN token authentication key) and not the
     * read-only key. It can create and delete videos, so it only lives on the server (env
     * {@code APP_CDN_BUNNYNET_APIKEY}) and never reaches the BFF or a browser. Secret: never logged and excluded
     * from {@link #toString()}. Blank by default; the in-memory library does not need it.
     */
    @ToString.Exclude
    private String apiKey = "";

    /** Base URL of the library's management API; only a test against a fake server changes it. Not a secret. */
    private String apiBaseUrl = DEFAULT_API_BASE_URL;

    /** Connect timeout of the management API calls, in milliseconds (same default as the Mercado Pago adapter). */
    private int apiConnectTimeoutMs = 2000;

    /** Read timeout of the management API calls, in milliseconds (same default as the Mercado Pago adapter). */
    private int apiReadTimeoutMs = 3000;

    /**
     * Name of the MP4 file served under {@code /<videoId>/} by Bunny Stream. Requires "MP4 Fallback" to be
     * enabled on the video library. A single path segment.
     */
    private String fileName = "play_720p.mp4";

    /**
     * Opt-in developer diagnostic, off by default (env {@code APP_CDN_BUNNYNET_DIAGNOSTICPROBE}). When {@code true}
     * and the real signer is selected, every signed URL is followed by one server-side {@code HEAD} request whose
     * status is logged (see {@link ProbingBunnyNetSignatureService}). Never enable it in production: it adds an
     * outbound request per signed URL.
     */
    private boolean diagnosticProbe;

    /**
     * Optional {@code Referer} the diagnostic probe sends with its {@code HEAD} (env
     * {@code APP_CDN_BUNNYNET_DIAGNOSTICPROBEREFERER}, blank by default = no {@code Referer}). A browser
     * {@code <video>} always sends one; without it the library option "Block Direct URL File Access" answers 403
     * even with a perfect token, and allowed/blocked domains are matched against the Referer hostname. Set it to
     * the origin of the BFF (for example {@code http://localhost:8080/}) to see what the browser would get. Only
     * validated (absolute http(s) URL) when {@link #diagnosticProbe} is on. Not a secret.
     */
    private String diagnosticProbeReferer = "";

    /**
     * Bare host of the pull zone: scheme and trailing slashes of the configured {@link #pullZoneHostname}
     * normalised away. Not a bean property on purpose (no {@code get} prefix).
     */
    public String pullZoneHost() {
        return bareHost(pullZoneHostname);
    }

    /**
     * The configured probe {@code Referer}, stripped; {@code null} when blank (no {@code Referer} is sent). Not a
     * bean property on purpose (no {@code get} prefix).
     */
    public String probeReferer() {
        return isBlank(diagnosticProbeReferer) ? null : diagnosticProbeReferer.strip();
    }

    /**
     * Base URL of the pull zone: always {@code https}, scheme and trailing slashes of the configured
     * {@link #pullZoneHostname} normalised away. Not a bean property on purpose (no {@code get} prefix).
     */
    public String pullZoneBaseUrl() {
        return "https://" + pullZoneHost();
    }

    /**
     * Fail-closed check applied whenever the real signer is selected. Reports every problem at once, naming
     * only the property, never its value.
     *
     * @throws IllegalStateException if the security key is blank, the host or library id is blank, still a
     *     placeholder or malformed, or the file name is blank or not a single path segment.
     */
    public void requireRealCdnConfiguration() {
        List<String> problems = new ArrayList<>();
        if (isBlank(securityKey)) {
            problems.add("app.cdn.bunny-net.security-key is missing (env APP_CDN_BUNNYNET_SECURITYKEY)");
        }
        String host = bareHost(pullZoneHostname);
        if (host.isEmpty() || host.contains(PLACEHOLDER_MARKER) || host.contains("/") || host.matches(".*\\s.*")) {
            problems.add("app.cdn.bunny-net.pull-zone-hostname is missing, a placeholder or malformed "
                + "(env APP_CDN_BUNNYNET_PULLZONEHOSTNAME)");
        }
        if (isBlank(videoLibraryId) || videoLibraryId.contains(PLACEHOLDER_MARKER)) {
            problems.add("app.cdn.bunny-net.video-library-id is missing or a placeholder "
                + "(env APP_CDN_BUNNYNET_VIDEOLIBRARYID)");
        }
        if (isBlank(fileName) || fileName.contains("/")) {
            problems.add("app.cdn.bunny-net.file-name must be a single file name (env APP_CDN_BUNNYNET_FILENAME)");
        }
        if (diagnosticProbe && probeReferer() != null && !isAbsoluteHttpUrl(probeReferer())) {
            problems.add("app.cdn.bunny-net.diagnostic-probe-referer must be an absolute http(s) URL such as "
                + "http://localhost:8080/ (env APP_CDN_BUNNYNET_DIAGNOSTICPROBEREFERER)");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                "Bunny.net CDN is not configured for token authentication: " + String.join("; ", problems));
        }
    }

    /**
     * Fail-closed check applied whenever the real video library client is selected. Reports every problem at once,
     * naming only the property, never its value.
     *
     * @throws IllegalStateException if the API key is blank or equal to the security key, the library id is not
     *     numeric, the base URL is not an absolute http(s) URL or a timeout is not positive
     */
    public void requireRealLibraryConfiguration() {
        List<String> problems = new ArrayList<>();
        if (isBlank(apiKey)) {
            problems.add("app.cdn.bunny-net.api-key is missing (env APP_CDN_BUNNYNET_APIKEY)");
        } else if (apiKey.equals(securityKey)) {
            problems.add("app.cdn.bunny-net.api-key must not be the same value as app.cdn.bunny-net.security-key");
        }
        if (isBlank(videoLibraryId) || !videoLibraryId.strip().matches("\\d+")) {
            problems.add("app.cdn.bunny-net.video-library-id must be the numeric id of the library "
                + "(env APP_CDN_BUNNYNET_VIDEOLIBRARYID)");
        }
        if (isBlank(apiBaseUrl) || !isAbsoluteHttpUrl(apiBaseUrl.strip())) {
            problems.add("app.cdn.bunny-net.api-base-url must be an absolute http(s) URL "
                + "(env APP_CDN_BUNNYNET_APIBASEURL)");
        }
        if (apiConnectTimeoutMs <= 0) {
            problems.add("app.cdn.bunny-net.api-connect-timeout-ms must be positive");
        }
        if (apiReadTimeoutMs <= 0) {
            problems.add("app.cdn.bunny-net.api-read-timeout-ms must be positive");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                "Bunny.net video library is not configured for the management API: " + String.join("; ", problems));
        }
    }

    private static boolean isAbsoluteHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return scheme != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String bareHost(String hostname) {
        String host = hostname == null ? "" : hostname.strip();
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) {
            host = host.substring("https://".length());
        } else if (lower.startsWith("http://")) {
            host = host.substring("http://".length());
        }
        int end = host.length();
        while (end > 0 && host.charAt(end - 1) == '/') {
            end--;
        }
        return host.substring(0, end);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
