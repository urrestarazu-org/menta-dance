package com.menta.physical.infrastructure.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BatchCreatePhysicalSessionsRequest(@NotNull LocalDate fromDate, @NotNull LocalDate toDate) {
}
