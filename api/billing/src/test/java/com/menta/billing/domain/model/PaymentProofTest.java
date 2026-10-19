package com.menta.billing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit coverage for {@link PaymentProof} (#31, US-BILLING-003, phase P3a, design C5/C11).
 */
class PaymentProofTest {

    private final PaymentId paymentId = PaymentId.of(UUID.randomUUID());
    private final Instant now = Instant.now();

    @Test
    void constructor_rejects_blank_storage_key() {
        assertThatThrownBy(() -> new PaymentProof(
            PaymentProofId.generate(), paymentId, " ", "comprobante.png", "image/png", 1024L, now
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_non_positive_size() {
        assertThatThrownBy(() -> new PaymentProof(
            PaymentProofId.generate(), paymentId, "key/proof.png", "comprobante.png", "image/png", 0L, now
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_null_uploaded_at() {
        assertThatThrownBy(() -> new PaymentProof(
            PaymentProofId.generate(), paymentId, "key/proof.png", "comprobante.png", "image/png", 1024L, null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_builds_storage_key_from_paymentId_and_generated_proofId_only() {
        PaymentProof proof = PaymentProof.create(paymentId, "image/png", "comprobante.png", 2048L, now);

        assertThat(proof.getStorageKey()).isEqualTo(paymentId.getValue() + "/" + proof.getId().getValue() + ".png");
        assertThat(proof.getPaymentId()).isEqualTo(paymentId);
        assertThat(proof.getContentType()).isEqualTo("image/png");
        assertThat(proof.getOriginalFilename()).isEqualTo("comprobante.png");
        assertThat(proof.getSizeBytes()).isEqualTo(2048L);
        assertThat(proof.getUploadedAt()).isEqualTo(now);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("acceptedContentTypes")
    void create_maps_each_validated_content_type_to_its_closed_extension(String contentType, String extension) {
        PaymentProof proof = PaymentProof.create(paymentId, contentType, "comprobante", 10L, now);

        assertThat(proof.getStorageKey()).endsWith("." + extension);
    }

    static Stream<Arguments> acceptedContentTypes() {
        return Stream.of(
            Arguments.of("image/png", "png"),
            Arguments.of("image/jpeg", "jpg"),
            Arguments.of("application/pdf", "pdf")
        );
    }

    @Test
    void create_rejects_content_type_outside_the_closed_extension_map() {
        assertThatThrownBy(() -> PaymentProof.create(paymentId, "image/gif", "comprobante.gif", 10L, now))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Storage-key factory hardening (task 3a.5, design C5): no client byte ever reaches a path. The
     * resulting {@code storageKey} always reduces to {@code {paymentId}/{proofId}.{ext}} regardless of
     * how hostile {@code originalFilename} is; the raw value is still preserved as metadata only.
     */
    @ParameterizedTest(name = "hostile filename: {0}")
    @MethodSource("hostileOriginalFilenames")
    void create_ignores_hostile_original_filename_when_building_storage_key(String hostileFilename) {
        PaymentProof proof = PaymentProof.create(paymentId, "image/png", hostileFilename, 10L, now);

        assertThat(proof.getStorageKey())
            .isEqualTo(paymentId.getValue() + "/" + proof.getId().getValue() + ".png");
        assertThat(proof.getStorageKey()).doesNotContain("..", "\0", hostileFilename);
        assertThat(proof.getOriginalFilename()).isEqualTo(hostileFilename);
    }

    static Stream<Arguments> hostileOriginalFilenames() {
        return Stream.of(
            Arguments.of("../../etc/passwd.png"),
            Arguments.of("a\0.png"),
            Arguments.of("a".repeat(300) + ".png"),
            Arguments.of("comprobante_transferencia_ñoño_日本語.png")
        );
    }
}
