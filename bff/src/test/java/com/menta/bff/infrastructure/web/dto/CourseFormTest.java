package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.dto.CreateCourseCommand;
import com.menta.bff.application.dto.UpdateCourseCommand;
import com.menta.bff.application.usecase.ManagedCourseView;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CourseForm")
class CourseFormTest {

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

    private static CourseForm valid() {
        CourseForm form = new CourseForm();
        form.setTitle(" Salsa ");
        form.setDescription("Nivel inicial");
        form.setLocalDay("TUESDAY");
        form.setLocalTime("22:00");
        form.setDurationMinutes("60");
        form.setLevel("BEGINNER");
        form.setCapacity("15");
        return form;
    }

    /** Field name to message, one message per field because each constraint is declared once per field. */
    private static Map<String, String> errorsOf(CourseForm form) {
        Map<String, String> errors = new TreeMap<>();
        validator.validate(form).forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return errors;
    }

    @Test
    @DisplayName("a complete form is valid and trims the texts")
    void validForm() {
        assertThat(errorsOf(valid())).isEmpty();
        assertThat(valid().getTitle()).isEqualTo("Salsa");
    }

    @Test
    @DisplayName("an empty form reports every required field in Spanish")
    void emptyForm() {
        assertThat(errorsOf(new CourseForm())).containsOnlyKeys("title", "description", "dayValid", "localTime",
                "durationMinutes", "levelValid", "capacity");
    }

    @ParameterizedTest(name = "duration/capacity \"{0}\" is refused")
    @ValueSource(strings = {"", "0", "-5", "abc", "1.5", "1234567"})
    void numbersMustBePositiveIntegers(String value) {
        CourseForm form = valid();
        form.setDurationMinutes(value);
        form.setCapacity(value);

        assertThat(errorsOf(form)).containsOnlyKeys("durationMinutes", "capacity");
    }

    @ParameterizedTest(name = "time \"{0}\" is refused")
    @ValueSource(strings = {"", "24:00", "9:30", "22:60", "10pm"})
    void timeMustBeHoursAndMinutes(String value) {
        CourseForm form = valid();
        form.setLocalTime(value);

        assertThat(errorsOf(form)).containsOnlyKeys("localTime");
    }

    @Test
    @DisplayName("day, level and status must be one of the known values")
    void enumeratedValues() {
        CourseForm form = valid();
        form.setLocalDay("FUNDAY");
        form.setLevel("EXPERT");
        form.setStatus("DELETED");

        assertThat(errorsOf(form)).containsOnlyKeys("dayValid", "levelValid", "statusValid");
    }

    @Test
    @DisplayName("a title longer than the column is refused")
    void longTitle() {
        CourseForm form = valid();
        form.setTitle("x".repeat(256));

        assertThat(errorsOf(form)).containsOnlyKeys("title");
    }

    @Test
    @DisplayName("builds the create command with the instructor given, never one from the form")
    void createCommand() {
        CourseForm form = valid();
        form.setProfessorId("ignored");

        assertThat(form.toCreateCommand("id-1", "Ada Lovelace")).isEqualTo(new CreateCourseCommand("Salsa",
                "Nivel inicial", "id-1", "Ada Lovelace", DayOfWeek.TUESDAY, LocalTime.of(22, 0), 60, "BEGINNER", 15));
    }

    @Test
    @DisplayName("builds the update command always carrying day and time together")
    void updateCommand() {
        CourseForm form = valid();
        form.setStatus("INACTIVE");

        assertThat(form.toUpdateCommand()).isEqualTo(new UpdateCourseCommand("Salsa", "Nivel inicial",
                DayOfWeek.TUESDAY, LocalTime.of(22, 0), 60, "BEGINNER", 15, "INACTIVE"));
    }

    @Test
    @DisplayName("is prefilled from a course with its local values")
    void prefilled() {
        CourseForm form = CourseForm.from(new ManagedCourseView("id", "Salsa", "Nivel inicial", "p", "Ada",
                DayOfWeek.TUESDAY, LocalTime.of(9, 5), "Martes 09:05", 60, "ADVANCED", 15, false));

        assertThat(form.getLocalDay()).isEqualTo("TUESDAY");
        assertThat(form.getLocalTime()).isEqualTo("09:05");
        assertThat(form.getDurationMinutes()).isEqualTo("60");
        assertThat(form.getCapacity()).isEqualTo("15");
        assertThat(form.getLevel()).isEqualTo("ADVANCED");
        assertThat(form.getStatus()).isEqualTo("INACTIVE");
        assertThat(errorsOf(form)).isEmpty();
    }

    @Test
    @DisplayName("exposes Spanish labels for the select options")
    void labels() {
        assertThat(CourseFormOptions.DAYS).containsEntry("TUESDAY", "Martes").hasSize(7);
        assertThat(CourseFormOptions.LEVELS).containsEntry("BEGINNER", "Principiante")
                .containsEntry("INTERMEDIATE", "Intermedio").containsEntry("ADVANCED", "Avanzado");
        assertThat(CourseFormOptions.levelLabel("BEGINNER")).isEqualTo("Principiante");
        assertThat(CourseFormOptions.levelLabel("OTHER")).isEqualTo("OTHER");
    }
}
