package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ResolveSessionWindowUseCaseImpl")
class ResolveSessionWindowUseCaseImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    /** 2026-11-15T01:00Z is still Saturday 2026-11-14 22:00 in Buenos Aires. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T01:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    private final ResolveSessionWindowUseCase useCase = new ResolveSessionWindowUseCaseImpl(CLOCK, ZONE);

    @Test
    @DisplayName("without parameters the window is today (in the academy's zone) to 90 days ahead")
    void defaultWindow() {
        SessionWindow window = useCase.execute(null, null);

        assertThat(window).isEqualTo(new SessionWindow(TODAY, TODAY.plusDays(90), true));
    }

    @Test
    @DisplayName("blank parameters count as absent")
    void blankIsAbsent() {
        assertThat(useCase.execute(" ", "")).isEqualTo(new SessionWindow(TODAY, TODAY.plusDays(90), true));
    }

    @Test
    @DisplayName("both parameters are taken as given")
    void bothGiven() {
        assertThat(useCase.execute("2026-12-01", "2026-12-31"))
                .isEqualTo(new SessionWindow(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31), true));
    }

    @Test
    @DisplayName("only the start: 90 days from it")
    void onlyFrom() {
        assertThat(useCase.execute("2026-12-01", null))
                .isEqualTo(new SessionWindow(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 3, 1), true));
    }

    @Test
    @DisplayName("only the end: from today to it")
    void onlyTo() {
        assertThat(useCase.execute(null, "2026-12-31"))
                .isEqualTo(new SessionWindow(TODAY, LocalDate.of(2026, 12, 31), true));
    }

    @ParameterizedTest(name = "[{index}] desde={0} hasta={1}")
    @CsvSource(nullValues = "null", value = {
        "garbage, null",
        "null, 2026-13-45",
        "2026-02-30, 2026-03-01",
        "2026-12-31, 2026-12-01",
        "null, 2026-11-01",
        "2026-01-01, 2027-06-01",
        "20261201, 20261231"})
    @DisplayName("a value that does not parse, an inverted window or one over 366 days falls back to the default")
    void invalidFallsBack(String from, String to) {
        SessionWindow window = useCase.execute(from, to);

        assertThat(window).isEqualTo(new SessionWindow(TODAY, TODAY.plusDays(90), false));
    }
}
