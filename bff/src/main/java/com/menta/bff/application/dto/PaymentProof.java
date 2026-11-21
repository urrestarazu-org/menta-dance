package com.menta.bff.application.dto;

/**
 * The transfer proof of a payment (#370): its bytes and the content type declared when it was uploaded, already
 * checked against the allowed ones. The array is not copied (a proof is up to 5 MB): nobody may modify it.
 *
 * @param content     the proof's bytes
 * @param contentType {@code image/png}, {@code image/jpeg} or {@code application/pdf}, without parameters
 */
public record PaymentProof(byte[] content, String contentType) {
}
