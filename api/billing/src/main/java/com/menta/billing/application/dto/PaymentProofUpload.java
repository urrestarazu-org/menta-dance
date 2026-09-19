package com.menta.billing.application.dto;

import java.util.Objects;

/**
 * Boundary record {@code PaymentController} converts a {@code MultipartFile} into (#31,
 * US-BILLING-003, design C11) — this is the repository's first {@code MultipartFile} boundary, and
 * nothing below the web layer is allowed to see a Spring or servlet type.
 *
 * @param declaredContentType the content type the client declared for the upload, unvalidated.
 * @param originalFilename the uploaded file's own name — metadata only, never used to build a
 *     storage path (design C5).
 * @param sizeBytes the uploaded file's declared size in bytes.
 * @param content the raw file bytes.
 */
public record PaymentProofUpload(String declaredContentType, String originalFilename, long sizeBytes, byte[] content) {

    public PaymentProofUpload {
        Objects.requireNonNull(declaredContentType, "declaredContentType cannot be null");
        Objects.requireNonNull(originalFilename, "originalFilename cannot be null");
        Objects.requireNonNull(content, "content cannot be null");
    }
}
