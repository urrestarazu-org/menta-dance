package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DescribeSessionConflictsUseCaseImpl")
class DescribeSessionConflictsUseCaseImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final DescribeSessionConflictsUseCase useCase = new DescribeSessionConflictsUseCaseImpl(ZONE);

    @Test
    @DisplayName("a UTC instant is worded in the academy's zone: Wednesday 01:00Z is Tuesday 17/11 22:00")
    void wordsTheInstantInTheAcademyZone() {
        assertThat(useCase.execute(List.of(Instant.parse("2026-11-18T01:00:00Z"))))
                .containsExactly("martes 17/11 22:00");
    }

    @Test
    @DisplayName("the labels come in chronological order, and a repeated instant is worded once")
    void sortedAndDistinct() {
        List<Instant> instants = List.of(Instant.parse("2026-12-02T01:00:00Z"), Instant.parse("2026-11-25T01:00:00Z"),
                Instant.parse("2026-12-02T01:00:00Z"));

        assertThat(useCase.execute(instants)).containsExactly("martes 24/11 22:00", "martes 01/12 22:00");
    }

    @Test
    @DisplayName("no instants, no labels")
    void empty() {
        assertThat(useCase.execute(List.of())).isEmpty();
    }
}
