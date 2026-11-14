package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("BatchForm")
class BatchFormTest {

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

    private static BatchForm form(String from, String to) {
        BatchForm form = new BatchForm();
        form.setFrom(from);
        form.setTo(to);
        return form;
    }

    private static Map<String, String> errorsOf(BatchForm form) {
        Map<String, String> errors = new TreeMap<>();
        validator.validate(form).forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return errors;
    }

    @Test
    @DisplayName("a month is valid, and its ends are the dates the user typed")
    void validRange() {
        BatchForm form = form(" 2026-11-01 ", "2026-11-30");

        assertThat(errorsOf(form)).isEmpty();
        assertThat(form.fromDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(form.toDate()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("a single day is a valid range")
    void singleDay() {
        assertThat(errorsOf(form("2026-11-17", "2026-11-17"))).isEmpty();
    }

    @Test
    @DisplayName("an empty form asks for both dates, in Spanish")
    void emptyForm() {
        assertThat(errorsOf(new BatchForm())).containsOnlyKeys("from", "to")
                .containsEntry("from", "Ingresá la fecha de inicio")
                .containsEntry("to", "Ingresá la fecha de fin");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"17/11/2026", "2026-02-30", "mañana"})
    @DisplayName("a date that is not a real yyyy-MM-dd one is refused, without a range error on top")
    void badDates(String date) {
        assertThat(errorsOf(form(date, "2026-11-30"))).containsOnlyKeys("from");
        assertThat(errorsOf(form("2026-11-01", date))).containsOnlyKeys("to");
    }

    @Test
    @DisplayName("an end before the start is refused next to the end")
    void invertedRange() {
        assertThat(errorsOf(form("2026-11-30", "2026-11-01"))).containsOnlyKeys("toOrdered")
                .containsEntry("toOrdered", "La fecha de fin no puede ser anterior a la de inicio");
    }

    @Test
    @DisplayName("366 days are fine and 367 are too many")
    void rangeCap() {
        assertThat(errorsOf(form("2026-01-01", "2027-01-01"))).isEmpty();

        assertThat(errorsOf(form("2026-01-01", "2027-01-02"))).containsOnlyKeys("toWithinLimit")
                .containsEntry("toWithinLimit", "El rango no puede superar los 366 días");
    }
}
