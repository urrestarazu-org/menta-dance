package com.menta.billing.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Proof of a bank transfer submitted against a {@link Payment} (#31, US-BILLING-003).
 *
 * <p>Its own aggregate, not a set of columns on {@code Payment} (design C5): {@code Payment} is
 * shared by Checkout Pro and physical purchases, which can never have a proof, so hanging these
 * five fields off it would leave every non-bank-transfer row with permanently-null columns.
 * {@code billing_payment_proofs} carries a unique key on {@code payment_id}, so a replacement is
 * an overwrite of one row — the prior proof being no longer retrievable is a structural property
 * of the schema, not a cleanup step someone must remember.</p>
 */
public final class PaymentProof {

    /**
     * Closed map from an already-validated content type to its filesystem extension (design C5/C11).
     * Never derived from {@code originalFilename} — the storage key scheme's entire path-traversal
     * safety rests on this being the only source of the extension.
     */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
        "image/png", "png",
        "image/jpeg", "jpg",
        "application/pdf", "pdf"
    );

    private final PaymentProofId id;
    private final PaymentId paymentId;
    private final String storageKey;
    private final String originalFilename;
    private final String contentType;
    private final long sizeBytes;
    private final Instant uploadedAt;

    public PaymentProof(
        PaymentProofId id, PaymentId paymentId, String storageKey, String originalFilename,
        String contentType, long sizeBytes, Instant uploadedAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId cannot be null");
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey cannot be null or blank");
        }
        this.storageKey = storageKey;
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename cannot be null or blank");
        }
        this.originalFilename = originalFilename;
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType cannot be null or blank");
        }
        this.contentType = contentType;
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive");
        }
        this.sizeBytes = sizeBytes;
        this.uploadedAt = Objects.requireNonNull(uploadedAt, "uploadedAt cannot be null");
    }

    /**
     * Server-side birth of a proof. {@code storageKey} is computed here from two server-generated
     * UUIDs (own id + {@code paymentId}) and the closed extension map over the caller-validated
     * {@code contentType} (design C11 — validation happens before this is called). {@code
     * originalFilename} is never read to build the key, only carried as metadata for the ops
     * notification, so a hostile filename — path traversal, an embedded NUL, an oversized or
     * non-ASCII name — has no path to influence where the blob lands (design C5).
     */
    public static PaymentProof create(
        PaymentId paymentId, String validatedContentType, String originalFilename, long sizeBytes,
        Instant uploadedAt
    ) {
        PaymentProofId id = PaymentProofId.generate();
        String extension = EXTENSION_BY_CONTENT_TYPE.get(validatedContentType);
        if (extension == null) {
            throw new IllegalArgumentException("Unsupported content type: " + validatedContentType);
        }
        String storageKey = paymentId.getValue() + "/" + id.getValue() + "." + extension;
        return new PaymentProof(
            id, paymentId, storageKey, originalFilename, validatedContentType, sizeBytes, uploadedAt
        );
    }

    public PaymentProofId getId() {
        return id;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
