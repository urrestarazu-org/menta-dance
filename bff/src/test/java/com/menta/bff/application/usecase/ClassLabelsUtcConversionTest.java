package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.menta.bff.application.usecase.ClassLabels.DateRange;
import com.menta.bff.application.usecase.ClassLabels.WeeklySlot;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ClassLabels local to UTC conversion")
class ClassLabelsUtcConversionTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @ParameterizedTest(name = "{0} {1} in Buenos Aires is {2} {3} UTC")
    @CsvSource({
        "TUESDAY, 22:00, WEDNESDAY, 01:00",
        "MONDAY, 22:00, TUESDAY, 01:00",
        "SUNDAY, 21:00, MONDAY, 00:00",
        "SUNDAY, 23:59, MONDAY, 02:59",
        "MONDAY, 00:00, MONDAY, 03:00",
        "MONDAY, 12:00, MONDAY, 15:00"})
    @DisplayName("toUtcSlot rolls the weekday forward with the hour west of UTC")
    void toUtcSlotWestOfUtc(DayOfWeek day, LocalTime time, DayOfWeek utcDay, LocalTime utcTime) {
        assertThat(ClassLabels.toUtcSlot(day, time, BUENOS_AIRES)).isEqualTo(new WeeklySlot(utcDay, utcTime));
    }

    @ParameterizedTest(name = "{0} {1} in Madrid is {2} {3} UTC")
    @CsvSource({
        "MONDAY, 00:30, SUNDAY, 23:30",
        "MONDAY, 01:00, MONDAY, 00:00",
        "WEDNESDAY, 18:00, WEDNESDAY, 17:00"})
    @DisplayName("toUtcSlot rolls the weekday backwards with the hour east of UTC")
    void toUtcSlotEastOfUtc(DayOfWeek day, LocalTime time, DayOfWeek utcDay, LocalTime utcTime) {
        assertThat(ClassLabels.toUtcSlot(day, time, MADRID)).isEqualTo(new WeeklySlot(utcDay, utcTime));
    }

    @Test
    @DisplayName("toLocalSlot is the inverse of toUtcSlot for every weekday and hour")
    void slotsRoundTrip() {
        for (ZoneId zone : List.of(BUENOS_AIRES, MADRID, ZoneId.of("UTC"), ZoneId.of("Asia/Tokyo"))) {
            for (DayOfWeek day : DayOfWeek.values()) {
                for (int hour = 0; hour < 24; hour++) {
                    LocalTime time = LocalTime.of(hour, 30);
                    WeeklySlot utc = ClassLabels.toUtcSlot(day, time, zone);
                    assertThat(ClassLabels.toLocalSlot(utc.day(), utc.time(), zone))
                            .as("%s %s in %s", day, time, zone).isEqualTo(new WeeklySlot(day, time));
                }
            }
        }
    }

    @Test
    @DisplayName("toLocalSlot reads a UTC course in the academy zone, as the label does")
    void toLocalSlotMatchesSchedule() {
        assertThat(ClassLabels.toLocalSlot(DayOfWeek.WEDNESDAY, LocalTime.of(1, 0), BUENOS_AIRES))
                .isEqualTo(new WeeklySlot(DayOfWeek.TUESDAY, LocalTime.of(22, 0)));
    }

    @Test
    @DisplayName("toUtc moves a local date and time to the UTC date and time, crossing midnight")
    void toUtcCrossesMidnight() {
        assertThat(ClassLabels.toUtc(LocalDate.of(2026, 11, 17), LocalTime.of(22, 0), BUENOS_AIRES))
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 1, 0));
        assertThat(ClassLabels.toUtc(LocalDate.of(2026, 11, 18), LocalTime.of(0, 30), MADRID))
                .isEqualTo(LocalDateTime.of(2026, 11, 17, 23, 30));
        assertThat(ClassLabels.toUtc(LocalDate.of(2026, 11, 17), LocalTime.of(12, 0), BUENOS_AIRES))
                .isEqualTo(LocalDateTime.of(2026, 11, 17, 15, 0));
    }

    @Test
    @DisplayName("a local range converts to the UTC dates that hold the same sessions: Tuesdays 22:00 of November")
    void batchRangeKeepsTheSameSessions() {
        // The course is Tuesday 22:00 in Buenos Aires, so Wednesday 01:00 UTC.
        LocalTime courseLocalTime = LocalTime.of(22, 0);
        LocalDate localFrom = LocalDate.of(2026, 11, 1);
        LocalDate localTo = LocalDate.of(2026, 11, 30);
        WeeklySlot utcSlot = ClassLabels.toUtcSlot(DayOfWeek.TUESDAY, courseLocalTime, BUENOS_AIRES);

        DateRange range = ClassLabels.toUtcDateRange(localFrom, localTo, courseLocalTime, BUENOS_AIRES);

        assertThat(range).isEqualTo(new DateRange(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 12, 1)));
        // What the API does: one session per UTC date in the range whose UTC weekday is the course's.
        List<LocalDate> utcDates = range.from().datesUntil(range.to().plusDays(1))
                .filter(date -> date.getDayOfWeek() == utcSlot.day()).toList();
        assertThat(utcDates).containsExactly(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 11),
                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 25));
        // And they are the four local Tuesdays of November at 22:00.
        Stream<LocalDate> localTuesdays = localFrom.datesUntil(localTo.plusDays(1))
                .filter(date -> date.getDayOfWeek() == DayOfWeek.TUESDAY);
        assertThat(localTuesdays.map(date -> ClassLabels.toUtc(date, courseLocalTime, BUENOS_AIRES).toLocalDate()))
                .containsExactlyElementsOf(utcDates);
    }

    @Test
    @DisplayName("an inverted local range stays inverted: the API refuses it")
    void invertedRangeStaysInverted() {
        DateRange range = ClassLabels.toUtcDateRange(
                LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), LocalTime.of(22, 0), BUENOS_AIRES);

        assertThat(range.from()).isAfter(range.to());
    }

    @Test
    @DisplayName("the helpers refuse null inputs")
    void refuseNulls() {
        assertThatNullPointerException().isThrownBy(() -> ClassLabels.toUtcSlot(null, LocalTime.NOON, MADRID));
        assertThatNullPointerException().isThrownBy(() -> ClassLabels.toLocalSlot(DayOfWeek.MONDAY, null, MADRID));
        assertThatNullPointerException().isThrownBy(() -> ClassLabels.toUtc(LocalDate.MIN, LocalTime.NOON, null));
        assertThatNullPointerException().isThrownBy(
                () -> ClassLabels.toUtcDateRange(LocalDate.MIN, null, LocalTime.NOON, MADRID));
    }
}
