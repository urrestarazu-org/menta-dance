package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.AssignedSession;
import com.menta.bff.application.port.out.PhysicalSessionsApiClient;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GetAssignedSessionsUseCase}. Which sessions count (window, holds,
 * cancellations) is decided upstream by {@code sessions/me}; this class only words their
 * instants in the academy's zone. A failed lookup is logged without token or body and
 * reads as an empty list, so the return page renders as it did before.
 */
@Slf4j
@RequiredArgsConstructor
public class GetAssignedSessionsUseCaseImpl implements GetAssignedSessionsUseCase {

    private static final String DEFAULT_COURSE_NAME = "Clase";

    private final PhysicalSessionsApiClient physicalSessionsApiClient;
    private final ZoneId displayZone;

    @Override
    public List<AssignedSessionRow> execute(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return List.of();
        }
        try {
            return physicalSessionsApiClient.findMySessions(accessToken).stream()
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
