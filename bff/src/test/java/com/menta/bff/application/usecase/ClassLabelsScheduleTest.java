package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ClassLabels.schedule")
class ClassLabelsScheduleTest {

    private static final ZoneId ACADEMY_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @ParameterizedTest(name = "{0} {1} UTC is {2} in the academy zone")
    @CsvSource({
        "MONDAY, 21:00, Lunes 18:00",
        "TUESDAY, 23:00, Martes 20:00",
        "WEDNESDAY, 01:00, Martes 22:00",
        "SUNDAY, 02:30, Sábado 23:30",
        "MONDAY, 02:30, Domingo 23:30",
        "FRIDAY, 03:00, Viernes 00:00",
        "FRIDAY, 02:59, Jueves 23:59",
        "saturday, 12:00, Sábado 09:00"})
    @DisplayName("converts both the weekday and the hour from UTC to the academy zone")
    void convertsWeekdayAndHour(String day, String time, String expected) {
        assertThat(ClassLabels.schedule(day, time, ACADEMY_ZONE)).isEqualTo(expected);
    }

    @Test
    @DisplayName("an academy zone ahead of UTC rolls the weekday forward")
    void zoneAheadOfUtc() {
        assertThat(ClassLabels.schedule("SUNDAY", "23:30", ZoneId.of("Asia/Tokyo"))).isEqualTo("Lunes 08:30");
    }

    @Test
    @DisplayName("UTC as the display zone changes nothing")
    void utcZoneIsIdentity() {
        assertThat(ClassLabels.schedule("MONDAY", "18:00", ZoneId.of("UTC"))).isEqualTo("Lunes 18:00");
    }

    @Test
    @DisplayName("tolerates a missing or unknown day and start time")
    void toleratesMissingParts() {
        assertThat(ClassLabels.schedule(null, null, ACADEMY_ZONE)).isNull();
        assertThat(ClassLabels.schedule("FUNDAY", " ", ACADEMY_ZONE)).isNull();
        assertThat(ClassLabels.schedule("FUNDAY", "13:00", ACADEMY_ZONE)).isEqualTo("10:00");
        assertThat(ClassLabels.schedule("SUNDAY", null, ACADEMY_ZONE)).isEqualTo("Domingo");
        assertThat(ClassLabels.schedule("SUNDAY", "", ACADEMY_ZONE)).isEqualTo("Domingo");
    }

    @Test
    @DisplayName("keeps a start time that is not HH:mm as it came")
    void keepsUnparseableTime() {
        assertThat(ClassLabels.schedule("MONDAY", "tarde", ACADEMY_ZONE)).isEqualTo("Lunes tarde");
        assertThat(ClassLabels.schedule(null, "tarde", ACADEMY_ZONE)).isEqualTo("tarde");
    }
}
