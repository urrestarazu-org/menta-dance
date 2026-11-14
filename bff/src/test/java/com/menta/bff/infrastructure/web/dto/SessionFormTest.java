package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.dto.CreateSessionCommand;
import com.menta.bff.application.usecase.ManagedCourseView;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.DayOfWeek;
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

@DisplayName("SessionForm")
class SessionFormTest {

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

    private static SessionForm valid() {
        SessionForm form = new SessionForm();
        form.setLocalDate("2026-11-17");
        form.setLocalTime("22:00");
        return form;
    }

    private static Map<String, String> errorsOf(SessionForm form) {
        Map<String, String> errors = new TreeMap<>();
        validator.validate(form).forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return errors;
    }

    @Test
    @DisplayName("a date and a time are enough: capacity and notes are optional")
    void validWithoutOptionals() {
        SessionForm form = valid();

        assertThat(errorsOf(form)).isEmpty();
        assertThat(form.toCommand()).isEqualTo(
                new CreateSessionCommand(LocalDate.of(2026, 11, 17), LocalTime.of(22, 0), null, null));
    }

    @Test
    @DisplayName("capacity and notes travel to the command, trimmed")
    void commandWithOptionals() {
        SessionForm form = valid();
        form.setCapacity(" 12 ");
        form.setNotes("  Traer zapatos  ");

        assertThat(errorsOf(form)).isEmpty();
        assertThat(form.toCommand()).isEqualTo(
                new CreateSessionCommand(LocalDate.of(2026, 11, 17), LocalTime.of(22, 0), 12, "Traer zapatos"));
    }

    @Test
    @DisplayName("blank capacity and notes are none")
    void blankOptionalsAreNone() {
        SessionForm form = valid();
        form.setCapacity("  ");
        form.setNotes("");

        assertThat(form.getCapacity()).isNull();
        assertThat(form.getNotes()).isNull();
        assertThat(errorsOf(form)).isEmpty();
    }

    @Test
    @DisplayName("an empty form asks for the date and the time, in Spanish")
    void emptyForm() {
        assertThat(errorsOf(new SessionForm())).containsOnlyKeys("localDate", "localTime")
                .containsEntry("localDate", "Ingresá la fecha de la sesión")
                .containsEntry("localTime", "Ingresá la hora con el formato HH:mm");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"17/11/2026", "2026-02-30", "2026-13-01", "mañana", "2026-11-1"})
    @DisplayName("a date that is not a real yyyy-MM-dd one is refused")
    void badDates(String date) {
        SessionForm form = valid();
        form.setLocalDate(date);

        assertThat(errorsOf(form)).containsOnlyKeys("localDate")
                .containsEntry("localDate", "Ingresá una fecha válida, con el formato aaaa-mm-dd");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"", "25:00", "9:00", "22:60", "22", "ab:cd"})
    @DisplayName("a time that is not HH:mm is refused")
    void badTimes(String time) {
        SessionForm form = valid();
        form.setLocalTime(time);

        assertThat(errorsOf(form)).containsOnlyKeys("localTime");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "1234567"})
    @DisplayName("a capacity that is not a positive whole number is refused")
    void badCapacities(String capacity) {
        SessionForm form = valid();
        form.setCapacity(capacity);

        assertThat(errorsOf(form)).containsOnlyKeys("capacity")
                .containsEntry("capacity", "Ingresá un cupo de 1 persona o más");
    }

    @Test
    @DisplayName("notes longer than the API's column are refused, 500 are fine")
    void notesLength() {
        SessionForm form = valid();
        form.setNotes("a".repeat(500));
        assertThat(errorsOf(form)).isEmpty();

        form.setNotes("a".repeat(501));
        assertThat(errorsOf(form)).containsOnlyKeys("notes");
    }

    @Test
    @DisplayName("the form of a course starts with the course's usual time")
    void startsWithTheCourseTime() {
        ManagedCourseView course = new ManagedCourseView("c", "Salsa", "d", "p", "Ada", DayOfWeek.TUESDAY,
                LocalTime.of(22, 0), "Martes 22:00", 60, "BEGINNER", 15, true);

        SessionForm form = SessionForm.forCourse(course);

        assertThat(form.getLocalTime()).isEqualTo("22:00");
        assertThat(form.getLocalDate()).isNull();
    }
}
