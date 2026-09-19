package com.menta.billing.domain.service;

import com.menta.billing.domain.exception.PaymentProofRejectedException;
import java.util.Set;

/**
 * Validates an uploaded bank-transfer proof (#31, US-BILLING-003, design C11) before it is ever
 * turned into a {@link com.menta.billing.domain.model.PaymentProof}. Pure, I/O-free and
 * Spring-free — this is the repository's first {@code MultipartFile} boundary, and nothing below
 * the web layer is allowed to see a Spring or servlet type; this class does not even see one.
 *
 * <table>
 *   <caption>Checks, in order</caption>
 *   <tr><th>Check</th><th>Rule</th></tr>
 *   <tr><td>Declared type</td><td>{@code image/png}, {@code image/jpeg} or {@code application/pdf}</td></tr>
 *   <tr><td>Size</td><td>&le; 5 MB</td></tr>
 *   <tr><td>Magic bytes</td><td>PNG / JPEG / {@code %PDF-} signature</td></tr>
 *   <tr><td>Agreement</td><td>sniffed type must equal the declared type</td></tr>
 * </table>
 */
public class PaymentProofContentValidator {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ACCEPTED_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    /**
     * @throws PaymentProofRejectedException whenever the declared type is not one of the three
     *     accepted types, the file is empty or larger than 5 MB, or the sniffed magic bytes do not
     *     agree with the declared type (a spoofed extension/content type).
     */
    public void validate(String declaredContentType, byte[] content) {
        if (declaredContentType == null || !ACCEPTED_TYPES.contains(declaredContentType)) {
            throw new PaymentProofRejectedException(
                "Unsupported declared content type: " + declaredContentType
            );
        }
        if (content == null || content.length == 0) {
            throw new PaymentProofRejectedException("Payment proof file is empty");
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw new PaymentProofRejectedException(
                "Payment proof file exceeds the maximum allowed size of 5MB"
            );
        }
        String sniffedType = sniff(content);
        if (sniffedType == null) {
            throw new PaymentProofRejectedException("Payment proof content does not match a supported file type");
        }
        if (!sniffedType.equals(declaredContentType)) {
            throw new PaymentProofRejectedException(
                "Declared content type " + declaredContentType + " does not match sniffed type " + sniffedType
            );
        }
    }

    private String sniff(byte[] content) {
        if (startsWith(content, PNG_MAGIC)) {
            return "image/png";
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(content, PDF_MAGIC)) {
            return "application/pdf";
        }
        return null;
    }

    private boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
