package com.menta.bff.application.usecase;

import java.time.LocalDate;

/**
 * The days of the sessions list of a course, in the academy's zone.
 *
 * @param from      first day, inclusive
 * @param to        last day, inclusive
 * @param requested {@code false} when the caller asked for a window that cannot be used (a value that is not a
 *                  date, an inverted window, one too wide) and this is the default instead; {@code true} when it
 *                  is what was asked for, or when nothing was asked
 */
public record SessionWindow(LocalDate from, LocalDate to, boolean requested) {
}
