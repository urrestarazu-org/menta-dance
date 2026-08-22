package com.menta.virtual.infrastructure.cdn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link StringFormatBunnyNetSignatureService}. The
 * implementation is a placeholder, but the URL shape is the contract a
 * future HMAC-backed impl must keep — verify that here so any refactor
 * breaks this test loudly.
 */
class StringFormatBunnyNetSignatureServiceTest {

    @Test
    void composes_url_from_pull_zone_library_and_video_id() {
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz-abc.b-cdn.net");
        properties.setVideoLibraryId("1234");

        StringFormatBunnyNetSignatureService service = new StringFormatBunnyNetSignatureService(properties);

        assertThat(service.generateSignedUrl("vid-xyz", 1_700_000_000L))
            .isEqualTo("vz-abc.b-cdn.net/1234/vid-xyz");
    }

    @Test
    void does_not_use_expiration_value_yet() {
        // The placeholder returns the same URL regardless of the TTL argument.
        // Documented in the implementation Javadoc — once HMAC is wired, the
        // TTL will surface inside the signed token. This test pins the
        // placeholder contract so the change shows up in the diff.
        BunnyNetProperties properties = new BunnyNetProperties();
        properties.setPullZoneHostname("vz.b-cdn.net");
        properties.setVideoLibraryId("1234");
        StringFormatBunnyNetSignatureService service = new StringFormatBunnyNetSignatureService(properties);

        assertThat(service.generateSignedUrl("vid", 0L))
            .isEqualTo(service.generateSignedUrl("vid", 9_999_999_999L));
    }
}
