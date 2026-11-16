package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * What the Billing API answers when the student cancels their subscription (#363).
 *
 * @param accessEndsAt instant until which the access lasts: cancelling stops the renewal, it does not
 *                     move the end of the paid period
 */
public record CancelledSubscription(Instant accessEndsAt) {
}
