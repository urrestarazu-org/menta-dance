package com.menta.auth.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import java.time.LocalDateTime;

/** A user as the admin endpoints show it. Never carries the password hash or the token version. */
public record UserSummaryResponse(
    String id,
    String email,
    String firstName,
    String lastName,
    Role role,
    UserStatus status,
    boolean demo,
    @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt
) {

    /** Copies the summary as the HTTP representation. */
    public static UserSummaryResponse from(UserSummary summary) {
        return new UserSummaryResponse(
            summary.id(), summary.email(), summary.firstName(), summary.lastName(), summary.role(),
            summary.status(), summary.demo(), summary.createdAt()
        );
    }
}
