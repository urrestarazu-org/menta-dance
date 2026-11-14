package com.menta.bff.application.usecase;

/**
 * Use case that decides which days the sessions list of a course shows (#368), from the optional range the user
 * typed. "Today" is the day in the academy's zone, from the injected clock. It never fails: whatever cannot be
 * used turns into the default window, and says so.
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface ResolveSessionWindowUseCase {

    /** Longest window the list shows, in days from the first to the last one, both included. */
    int MAX_DAYS = 366;

    /** Days the default window spans after today. */
    int DEFAULT_DAYS_AHEAD = 90;

    /**
     * Resolves the window.
     *
     * @param from first day as typed ({@code yyyy-MM-dd}), possibly {@code null} or blank
     * @param to   last day as typed ({@code yyyy-MM-dd}), possibly {@code null} or blank
     * @return the window: with only a first day, {@value #DEFAULT_DAYS_AHEAD} days from it; with only a last day,
     *         from today to it; with none, from today to {@value #DEFAULT_DAYS_AHEAD} days ahead. An unusable
     *         request gives the default window with {@code requested = false}
     */
    SessionWindow execute(String from, String to);
}
