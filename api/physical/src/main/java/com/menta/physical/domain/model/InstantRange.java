package com.menta.physical.domain.model;

import com.menta.physical.domain.exception.InvalidSessionRangeException;
import java.time.Duration;
import java.time.Instant;

/**
 * A half-open range of UTC instants {@code [from, to)} that a read receives already computed by its
 * client (ADR-0048, #355, #729). Every backend date is UTC, so the range carries no time zone and no
 * day arithmetic: the client turns the local day or month of its user into the two bounds.
 *
 * <p>The range is never empty or inverted ({@code from} is strictly before {@code to}) and never wider
 * than the maximum its read allows; exactly the maximum is accepted, one nanosecond more is not.
 */
public record InstantRange(Instant from, Instant to) {

    /**
     * Validates and builds the range.
     *
     * @param maxSpan the widest span the read accepts, in whole days
     * @throws InvalidSessionRangeException when {@code from} is not before {@code to} or the span exceeds
     *     {@code maxSpan}
     */
    public static InstantRange within(Instant from, Instant to, Duration maxSpan) {
        if (!from.isBefore(to) || Duration.between(from, to).compareTo(maxSpan) > 0) {
            throw new InvalidSessionRangeException(maxSpan.toDays());
        }
        return new InstantRange(from, to);
    }
}
