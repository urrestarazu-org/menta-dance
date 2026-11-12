package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.PhysicalDaySessionView;
import com.menta.physical.domain.model.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** #355 — wire shape of {@code GET /api/v1/admin/physical/sessions?date=}. */
public record PhysicalDaySessionsResponse(List<Item> sessions) {

    /** One session of the day. */
    public record Item(
        UUID sessionId, UUID courseId, String courseName, Instant scheduledAt, Instant endsAt,
        SessionStatus status, int capacity, int occupied
    ) {

        static Item from(PhysicalDaySessionView view) {
            return new Item(
                view.sessionId(), view.courseId(), view.courseName(), view.scheduledAt(), view.endsAt(),
                view.status(), view.capacity(), view.occupied()
            );
        }
    }

    /** Maps the application views to the wire shape, keeping every value and the order as is. */
    public static PhysicalDaySessionsResponse from(List<PhysicalDaySessionView> views) {
        return new PhysicalDaySessionsResponse(views.stream().map(Item::from).toList());
    }
}
