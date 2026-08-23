package com.menta.virtual.infrastructure.web.dto;

import com.menta.virtual.application.dto.PublicStreamQuality;

/**
 * Wire-level mirror of {@link PublicStreamQuality}; the JSON shape
 * is identical {@code {label, bitrate}} but the field lives in
 * {@code infrastructure.web.dto} so it never leaks onto a request
 * coming from outside the module.
 */
public record PublicStreamQualityDto(String label, long bitrate) {

    public static PublicStreamQualityDto from(PublicStreamQuality quality) {
        return new PublicStreamQualityDto(quality.label(), quality.bitrate());
    }
}
