package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.AssignedSessionView;
import com.menta.physical.application.dto.AssignedSessionsView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** #356 — wire shape of {@code GET /api/v1/physical/sessions/me}. */
public record MySessionsResponse(Instant from, Instant to, List<Item> sessions) {

    /** One session the student can attend. */
    public record Item(
        UUID sessionId, UUID courseId, String courseName, String instructorName,
        Instant scheduledAt, boolean checkInOpen
    ) {

        static Item from(AssignedSessionView view) {
            return new Item(
                view.sessionId(), view.courseId(), view.courseName(), view.instructorName(),
                view.scheduledAt(), view.checkInOpen()
            );
        }
    }

    /** Maps the application view to the wire shape, keeping every value as is. */
    public static MySessionsResponse from(AssignedSessionsView view) {
        return new MySessionsResponse(view.from(), view.to(), view.sessions().stream().map(Item::from).toList());
    }
}
