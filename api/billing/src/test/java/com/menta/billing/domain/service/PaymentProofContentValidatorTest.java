package com.menta.billing.domain.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.billing.domain.exception.PaymentProofRejectedException;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Table-driven coverage for {@link PaymentProofContentValidator} (#31, US-BILLING-003, phase P3a,
 * design C11). Pure domain service — no Spring context needed.
 */
class PaymentProofContentValidatorTest {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final PaymentProofContentValidator validator = new PaymentProofContentValidator();

    @ParameterizedTest(name = "accepted: {0}")
    @MethodSource("acceptedFiles")
    void accepts_each_supported_type_when_declared_matches_sniffed(String contentType, byte[] content) {
        assertThatCode(() -> validator.validate(contentType, content)).doesNotThrowAnyException();
    }

    static Stream<Arguments> acceptedFiles() {
        return Stream.of(
            Arguments.of("image/png", bodyOf(PNG_MAGIC, 1024)),
            Arguments.of("image/jpeg", bodyOf(JPEG_MAGIC, 1024)),
            Arguments.of("application/pdf", bodyOf(PDF_MAGIC, 1024))
        );
    }

    @org.junit.jupiter.api.Test
    void rejects_a_file_larger_than_5mb() {
        byte[] oversized = bodyOf(PNG_MAGIC, 5 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> validator.validate("image/png", oversized))
            .isInstanceOf(PaymentProofRejectedException.class);
    }

    @org.junit.jupiter.api.Test
    void rejects_when_declared_type_does_not_match_sniffed_magic_bytes() {
        byte[] actuallyPdf = bodyOf(PDF_MAGIC, 1024);

        assertThatThrownBy(() -> validator.validate("image/jpeg", actuallyPdf))
            .isInstanceOf(PaymentProofRejectedException.class);
    }

    @org.junit.jupiter.api.Test
    void rejects_an_empty_file() {
        assertThatThrownBy(() -> validator.validate("image/png", new byte[0]))
            .isInstanceOf(PaymentProofRejectedException.class);
    }

    /** A PDF renamed to {@code .png} — the declared type lies, and the magic bytes catch it. */
    @org.junit.jupiter.api.Test
    void rejects_a_pdf_renamed_with_a_png_extension() {
        byte[] actuallyPdf = bodyOf(PDF_MAGIC, 2048);

        assertThatThrownBy(() -> validator.validate("image/png", actuallyPdf))
            .isInstanceOf(PaymentProofRejectedException.class);
    }

    @org.junit.jupiter.api.Test
    void rejects_a_declared_type_outside_the_whitelist() {
        byte[] gif = bodyOf(new byte[] {'G', 'I', 'F', '8'}, 128);

        assertThatThrownBy(() -> validator.validate("image/gif", gif))
            .isInstanceOf(PaymentProofRejectedException.class);
    }

    private static byte[] bodyOf(byte[] magic, int totalSize) {
        byte[] content = new byte[totalSize];
        Arrays.fill(content, (byte) 'x');
        System.arraycopy(magic, 0, content, 0, magic.length);
        return content;
    }
}
