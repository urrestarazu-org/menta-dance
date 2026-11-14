package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.dto.UpdateSessionCommand;
import com.menta.bff.application.usecase.ManagedSessionView;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("RescheduleForm")
class RescheduleFormTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static RescheduleForm form(String date, String time) {
        RescheduleForm form = new RescheduleForm();
        form.setLocalDate(date);
        form.setLocalTime(time);
        return form;
    }

    private static Map<String, String> errorsOf(RescheduleForm form) {
        Map<String, String> errors = new TreeMap<>();
        validator.validate(form).forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return errors;
    }

    @Test
    @DisplayName("a date and a time are a valid change, and they are all the command changes: always together")
    void validChange() {
        RescheduleForm form = form(" 2026-11-24 ", " 20:30 ");

        assertThat(errorsOf(form)).isEmpty();
        assertThat(form.toCommand()).isEqualTo(
                new UpdateSessionCommand(LocalDate.of(2026, 11, 24), LocalTime.of(20, 30), null, null, null));
    }

    @Test
    @DisplayName("an empty form asks for both, in Spanish")
    void emptyForm() {
        assertThat(errorsOf(new RescheduleForm())).containsOnlyKeys("localDate", "localTime")
                .containsEntry("localDate", "Ingresá la nueva fecha")
                .containsEntry("localTime", "Ingresá la hora con el formato HH:mm");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"24/11/2026", "2026-02-30", "pronto"})
    @DisplayName("a date that is not a real yyyy-MM-dd one is refused")
    void badDates(String date) {
        assertThat(errorsOf(form(date, "20:30"))).containsOnlyKeys("localDate");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"", "24:00", "8:30", "20:75"})
    @DisplayName("a time that is not HH:mm is refused")
    void badTimes(String time) {
        assertThat(errorsOf(form("2026-11-24", time))).containsOnlyKeys("localTime");
    }

    @Test
    @DisplayName("the form of a session starts with its current time and no date")
    void startsWithTheCurrentTime() {
        ManagedSessionView session = new ManagedSessionView("s", "c", Instant.parse("2026-11-18T01:00:00Z"),
                LocalDate.of(2026, 11, 17), LocalTime.of(22, 0), "martes 17 de noviembre a las 22:00", false, 15, 3,
                12, null);

        RescheduleForm form = RescheduleForm.from(session);

        assertThat(form.getLocalTime()).isEqualTo("22:00");
        assertThat(form.getLocalDate()).isNull();
    }
}
