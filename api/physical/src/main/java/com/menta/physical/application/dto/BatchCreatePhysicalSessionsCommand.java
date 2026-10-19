package com.menta.physical.application.dto;

import java.time.LocalDate;

/**
 * US-PHYSICAL-006 escenario 2: one session is generated per date in {@code
 * [fromDate, toDate]} that falls on the parent course's {@code dayOfWeek},
 * at the course's {@code startTime}, with the course's {@code capacity} — no
 * per-session override in the batch contract. No holiday exclusion: the
 * project has no holidays concept yet (verified — zero references anywhere
 * in code or config), and the issue's own wording ("excluyendo feriados si
 * están configurados") only applies when that configuration exists.
 */
public record BatchCreatePhysicalSessionsCommand(LocalDate fromDate, LocalDate toDate) {
}
