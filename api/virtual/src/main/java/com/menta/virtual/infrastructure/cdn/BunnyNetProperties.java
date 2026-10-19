package com.menta.virtual.infrastructure.cdn;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Bunny.net pull zone + video library used by the
 * lesson-stream pipeline (US-VIRTUAL-004). Bound from properties keyed
 * under {@code app.cdn.bunny-net}.
 *
 * <p>The defaults here are deliberate placeholders flagged with
 * {@code invalid-missing} / {@code missing-config}. They let integration
 * tests boot the context without setting these properties; if a build
 * pushes this configuration to a real environment, the placeholders
 * are visually obvious in logs and metrics.</p>
 *
 * <p>A complementary runtime check on production is a follow-up that
 * belongs in {@link com.menta.virtual.infrastructure.config.VirtualConfiguration}:
 * the placeholder defaults are explicit enough to detect a deployment
 * misconfiguration, but the check that aborts boot when they leak
 * outside the test profile was intentionally deferred to keep this PR
 * scoped to the CI fix.</p>
 */
@Data
@ConfigurationProperties(prefix = "app.cdn.bunny-net")
public class BunnyNetProperties {

    /**
     * Hostname of the Bunny.net pull zone (e.g. {@code vz-abc123.b-cdn.net}).
     * Combined with {@link #videoLibraryId} + the per-lesson {@code videoId}
     * by {@link BunnyNetSignatureService}.
     */
    private String pullZoneHostname = "https://invalid-missing-config.local";

    /**
     * Bunny.net video-library id (e.g. {@code 12345}). Combined with
     * {@link #pullZoneHostname} + the per-lesson {@code videoId} by
     * {@link BunnyNetSignatureService}.
     */
    private String videoLibraryId = "missing-config";
}
