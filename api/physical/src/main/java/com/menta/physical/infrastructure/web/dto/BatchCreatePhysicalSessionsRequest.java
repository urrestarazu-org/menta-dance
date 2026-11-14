package com.menta.physical.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Request body to generate the sessions of a course over a date range.
 *
 * <p>Both dates are mandatory ({@code @NotNull}) and the range is inclusive. An inverted range, or
 * one with no date on the course's day of week, is rejected (see {@code EmptySessionBatchException}).
 */
public record BatchCreatePhysicalSessionsRequest(@NotNull LocalDate fromDate, @NotNull LocalDate toDate) {
}
