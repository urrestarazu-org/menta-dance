package com.menta.physical.infrastructure.web.dto;

import com.menta.physical.application.dto.PhysicalRangeSessionView;
import com.menta.physical.domain.model.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** #355 — wire shape of {@code GET /api/v1/admin/physical/sessions?from=&to=}. */
public record PhysicalRangeSessionsResponse(List<Item> sessions) {

    /** One session of the range. */
    public record Item(
        UUID sessionId, UUID courseId, String courseName, Instant scheduledAt, Instant endsAt,
        SessionStatus status, int capacity, int occupied
    ) {

        static Item from(PhysicalRangeSessionView view) {
            return new Item(
                view.sessionId(), view.courseId(), view.courseName(), view.scheduledAt(), view.endsAt(),
                view.status(), view.capacity(), view.occupied()
            );
        }
    }

    /** Maps the application views to the wire shape, keeping every value and the order as is. */
    public static PhysicalRangeSessionsResponse from(List<PhysicalRangeSessionView> views) {
        return new PhysicalRangeSessionsResponse(views.stream().map(Item::from).toList());
    }
}
