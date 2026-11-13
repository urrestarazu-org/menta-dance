package com.menta.bff.application.dto;

import java.time.Instant;

/**
 * The attendance a manual check-in left (or had already left) in the API.
 *
 * @param alreadyRegistered {@code true} when the API answered {@code 200}: an idempotent replay of an
 *                          attendance that existed, so nothing was created; {@code false} for the {@code 201}
 * @param recordedAt        instant the attendance was recorded
 * @param kind              {@code QR} or {@code MANUAL}: how the attendance was recorded
 */
public record CheckInReceipt(boolean alreadyRegistered, Instant recordedAt, String kind) {
}
