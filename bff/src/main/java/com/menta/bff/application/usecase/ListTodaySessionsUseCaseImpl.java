package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.StaffSession;
import com.menta.bff.application.port.out.PhysicalReceptionApiClient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link ListTodaySessionsUseCase}. The backend works in UTC only (ADR-0048), so the
 * conversion of "today" is done here: the current instant comes from the injected {@link Clock}, the day is the
 * local one in the academy's display zone and the range sent upstream is its start and the start of the next
 * day, as UTC instants. A failed lookup is logged without token or rows and reads as an unavailable view.
 */
@Slf4j
@RequiredArgsConstructor
public class ListTodaySessionsUseCaseImpl implements ListTodaySessionsUseCase {

    private final PhysicalReceptionApiClient receptionApiClient;
    private final Clock clock;
    private final ZoneId displayZone;

    @Override
    public ReceptionSessionsView execute(String accessToken) {
        Instant now = clock.instant();
        String dayLabel = ClassLabels.day(now, displayZone);
        if (accessToken == null || accessToken.isBlank()) {
            return ReceptionSessionsView.unavailableView(dayLabel);
        }
        LocalDate today = now.atZone(displayZone).toLocalDate();
        Instant from = today.atStartOfDay(displayZone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(displayZone).toInstant();
        try {
            List<ReceptionSessionsView.Row> rows = receptionApiClient.listSessions(accessToken, from, to).stream()
                    .map(this::toRow)
                    .toList();
            return new ReceptionSessionsView(dayLabel, rows, false);
        } catch (PhysicalReceptionApiClient.ServiceUnavailableException e) {
            log.warn("Reception agenda unavailable, rendering the screen without it: {}", e.getMessage());
            return ReceptionSessionsView.unavailableView(dayLabel);
        }
    }

    private ReceptionSessionsView.Row toRow(StaffSession session) {
        return new ReceptionSessionsView.Row(session.sessionId(), session.courseName(), session.scheduledAt(),
                ClassLabels.time(session.scheduledAt(), displayZone), session.cancelled(), session.capacity(),
                session.occupied());
    }
}
