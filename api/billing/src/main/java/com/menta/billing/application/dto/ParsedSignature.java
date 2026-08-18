package com.menta.billing.application.dto;

/** {@code x-signature} header, split into its {@code ts} and {@code v1} fields. */
public record ParsedSignature(String timestamp, String hash) {
}
