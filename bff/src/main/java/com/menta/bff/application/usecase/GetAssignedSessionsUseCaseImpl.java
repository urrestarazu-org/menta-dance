package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.AssignedSession;
import com.menta.bff.application.port.out.PhysicalSessionsApiClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetAssignedSessionsUseCase}. The backend works in UTC and needs the range
 * (ADR-0048): the payment return lists the sessions from one day ago to 31 days ahead of the instant given by
 * the injected {@link Clock}. Holds and cancellations are decided upstream by {@code sessions/me}; this class
 * only words the instants in the academy's zone. A failed lookup is logged without token or body and
 * reads as an empty list, so the return page renders as it did before.
 */
@Slf4j
@RequiredArgsConstructor
public class GetAssignedSessionsUseCaseImpl implements GetAssignedSessionsUseCase {

    private static final String DEFAULT_COURSE_NAME = "Clase";

    private static final Duration LOOK_BACK = Duration.ofDays(1);
    private static final Duration LOOK_AHEAD = Duration.ofDays(31);

    private final PhysicalSessionsApiClient physicalSessionsApiClient;
    private final Clock clock;
    private final ZoneId displayZone;

    @Override
    public List<AssignedSessionRow> execute(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return List.of();
        }
        Instant now = clock.instant();
        try {
            return physicalSessionsApiClient.findMySessions(accessToken, now.minus(LOOK_BACK), now.plus(LOOK_AHEAD))
                    .stream()
                    .sorted(Comparator.comparing(AssignedSession::scheduledAt))
                    .map(s -> new AssignedSessionRow(
                            s.sessionId(),
                            s.courseName() == null || s.courseName().isBlank() ? DEFAULT_COURSE_NAME : s.courseName(),
                            ClassLabels.moment(s.scheduledAt(), displayZone)))
                    .toList();
        } catch (PhysicalSessionsApiClient.ServiceUnavailableException e) {
            log.warn("Student sessions unavailable, rendering the payment return without them: {}",
                    e.getMessage());
            return List.of();
        }
    }
}
