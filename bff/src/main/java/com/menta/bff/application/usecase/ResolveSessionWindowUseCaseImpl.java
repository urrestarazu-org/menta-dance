package com.menta.bff.application.usecase;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ResolveSessionWindowUseCase}. */
@RequiredArgsConstructor
public class ResolveSessionWindowUseCaseImpl implements ResolveSessionWindowUseCase {

    private final Clock clock;
    private final ZoneId displayZone;

    @Override
    public SessionWindow execute(String from, String to) {
        LocalDate today = clock.instant().atZone(displayZone).toLocalDate();
        SessionWindow fallback = new SessionWindow(today, today.plusDays(DEFAULT_DAYS_AHEAD), false);
        boolean noFrom = from == null || from.isBlank();
        boolean noTo = to == null || to.isBlank();
        if (noFrom && noTo) {
            return new SessionWindow(today, today.plusDays(DEFAULT_DAYS_AHEAD), true);
        }
        try {
            LocalDate first = noFrom ? today : LocalDate.parse(from.strip());
            LocalDate last = noTo ? first.plusDays(DEFAULT_DAYS_AHEAD) : LocalDate.parse(to.strip());
            boolean usable = !last.isBefore(first) && ChronoUnit.DAYS.between(first, last) < MAX_DAYS;
            return usable ? new SessionWindow(first, last, true) : fallback;
        } catch (DateTimeException e) {
            return fallback;
        }
    }
}
