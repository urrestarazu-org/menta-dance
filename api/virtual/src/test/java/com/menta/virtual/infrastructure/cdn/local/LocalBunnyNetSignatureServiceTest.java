package com.menta.virtual.infrastructure.cdn.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.infrastructure.cdn.BunnyNetProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link LocalBunnyNetSignatureService} (ADR-0040,
 * issue #129). Proves the local signed-URL contract is deterministic,
 * TTL-derived and credential-free WITHOUT a Spring context — profile
 * selection (which bean wins under {@code e2e-bunny-net}) is covered
 * separately by {@code VirtualConfigurationTest}.
 */
class LocalBunnyNetSignatureServiceTest {

    private static final String SALT = "menta-local-e2e";

    @Test
    void same_video_id_and_ttl_produce_the_same_signature_twice() {
        LocalBunnyNetSignatureService service =
            new LocalBunnyNetSignatureService(properties("https://vz-local.invalid", "e2e-library"));

        String first = service.generateSignedUrl("lesson-video-1", 1_800_000_000L);
        String second = service.generateSignedUrl("lesson-video-1", 1_800_000_000L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void expiry_query_parameter_carries_the_caller_supplied_ttl_verbatim() {
        LocalBunnyNetSignatureService service =
            new LocalBunnyNetSignatureService(properties("https://vz-local.invalid", "e2e-library"));

        String url = service.generateSignedUrl("lesson-video-1", 1_800_000_000L);

        assertThat(url).contains("exp=1800000000");
    }

    @Test
    void signed_url_follows_the_pull_zone_library_video_exp_sig_shape() {
        LocalBunnyNetSignatureService service =
            new LocalBunnyNetSignatureService(properties("https://vz-local.invalid", "e2e-library"));

        String url = service.generateSignedUrl("lesson-video-1", 1_800_000_000L);

        assertThat(url).matches(
            "https://vz-local\\.invalid/e2e-library/lesson-video-1\\?exp=1800000000&sig=[0-9a-f]{64}"
        );
    }

    @Test
    void signature_changes_when_the_video_id_changes_proving_it_is_not_hardcoded() {
        BunnyNetProperties properties = properties("https://vz-local.invalid", "e2e-library");
        LocalBunnyNetSignatureService service = new LocalBunnyNetSignatureService(properties);

        String urlForFirstVideo = service.generateSignedUrl("lesson-video-1", 1_800_000_000L);
        String urlForSecondVideo = service.generateSignedUrl("lesson-video-2", 1_800_000_000L);

        assertThat(sigOf(urlForFirstVideo)).isNotEqualTo(sigOf(urlForSecondVideo));
    }

    @Test
    void signature_matches_the_documented_sha_256_formula_and_carries_no_real_bunny_net_credential()
        throws Exception {
        BunnyNetProperties properties = properties("https://vz-local.invalid", "e2e-library");
        LocalBunnyNetSignatureService service = new LocalBunnyNetSignatureService(properties);
        long exp = 1_800_000_000L;

        String url = service.generateSignedUrl("lesson-video-1", exp);

        String expectedSig = sha256Hex(SALT + "|e2e-library|lesson-video-1|" + exp);
        assertThat(sigOf(url)).isEqualTo(expectedSig);
        assertThat(url).doesNotContain("Bearer").doesNotContain("access-token")
            .doesNotContain("apikey");
    }

    private static BunnyNetProperties properties(String pullZone, String libraryId) {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname(pullZone);
        properties.setVideoLibraryId(libraryId);
        return properties;
    }

    private static String sigOf(String url) {
        int index = url.indexOf("sig=");
        return url.substring(index + "sig=".length());
    }

    private static String sha256Hex(String payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
