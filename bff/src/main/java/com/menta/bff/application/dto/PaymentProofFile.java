package com.menta.bff.application.dto;

/**
 * A proof of transfer chosen by the student. The Billing API validates type, size and content.
 *
 * @param fileName    name the browser sent
 * @param contentType declared media type
 * @param content     file bytes; never logged
 */
public record PaymentProofFile(String fileName, String contentType, byte[] content) {
}
