package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.dto.CreateVirtualLessonCommand;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualLessonDetail;
import com.menta.bff.application.dto.VirtualLessonChanges;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("LessonForm")
class LessonFormTest {

    private static final String DURATION = "Ingresá la duración en minutos: un número entero de 0 a 9999.";

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

    private static LessonForm valid() {
        LessonForm form = new LessonForm();
        form.setTitle("Primer paso");
        form.setDescription("Descripción");
        return form;
    }

    private static Map<String, String> errorsOf(LessonForm form) {
        Map<String, String> errors = new TreeMap<>();
        validator.validate(form).forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return errors;
    }

    static Stream<Arguments> limits() {
        BiConsumer<LessonForm, String> title = LessonForm::setTitle;
        BiConsumer<LessonForm, String> description = LessonForm::setDescription;
        BiConsumer<LessonForm, String> videoId = LessonForm::setVideoId;
        return Stream.of(
                Arguments.of("title", title, 255, "El título puede tener hasta 255 caracteres."),
                Arguments.of("description", description, 5000,
                        "La descripción puede tener hasta 5000 caracteres."),
                Arguments.of("videoId", videoId, 128, "El identificador del video puede tener hasta 128 caracteres."));
    }

    @ParameterizedTest(name = "{0}: {2} characters pass, one more fails")
    @MethodSource("limits")
    void textLimits(String field, BiConsumer<LessonForm, String> setter, int max, String message) {
        LessonForm atLimit = valid();
        setter.accept(atLimit, "x".repeat(max));
        LessonForm over = valid();
        setter.accept(over, "x".repeat(max + 1));

        assertThat(errorsOf(atLimit)).isEmpty();
        assertThat(errorsOf(over)).containsOnlyKeys(field).containsEntry(field, message);
    }

    @ParameterizedTest(name = "blank \"{0}\" is refused")
    @ValueSource(strings = {"", "   "})
    void titleAndDescriptionAreRequired(String blank) {
        LessonForm form = new LessonForm();
        form.setTitle(blank);
        form.setDescription(blank);

        assertThat(errorsOf(form)).containsOnlyKeys("title", "description")
                .containsEntry("title", "Ingresá el título de la lección.")
                .containsEntry("description", "Ingresá la descripción de la lección.");
        assertThat(errorsOf(new LessonForm())).containsOnlyKeys("title", "description");
    }

    @ParameterizedTest(name = "video id \"{0}\" is refused")
    @ValueSource(strings = {"two words", "tab\tinside", "trailing "})
    void videoIdHasNoSpaces(String videoId) {
        LessonForm form = valid();
        form.setVideoId(videoId);

        assertThat(errorsOf(form)).containsOnlyKeys("videoId")
                .containsEntry("videoId", "El identificador del video no puede tener espacios.");
    }

    @ParameterizedTest(name = "duration \"{0}\" passes: {1} minutes")
    @CsvSource(value = {"'',0", "0,0", "45,45", "9999,9999", "0007,7"})
    void validDurations(String minutes, int expected) {
        LessonForm form = valid();
        form.setDurationMinutes(minutes);

        assertThat(errorsOf(form)).isEmpty();
        assertThat(form.toCommand().durationMinutes()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "duration \"{0}\" is refused")
    @ValueSource(strings = {"-1", "abc", "1.5", "10000", " 5", "1e3"})
    void invalidDurations(String minutes) {
        LessonForm form = valid();
        form.setDurationMinutes(minutes);

        assertThat(errorsOf(form)).containsOnlyKeys("durationMinutes").containsEntry("durationMinutes", DURATION);
    }

    @Test
    @DisplayName("a new form starts with duration 0; an empty video id is not sent and the creation has no order")
    void commands() {
        LessonForm form = valid();
        form.setVideoId("");
        assertThat(form.getDurationMinutes()).isEqualTo("0");
        assertThat(form.toCommand()).isEqualTo(
                new CreateVirtualLessonCommand("Primer paso", "Descripción", null, 0, false, null));
        assertThat(form.toChanges()).isEqualTo(
                new VirtualLessonChanges("Primer paso", "Descripción", null, 0, false, null));

        form.setVideoId("video-1");
        form.setFree(true);
        assertThat(form.toCommand().videoId()).isEqualTo("video-1");
        assertThat(form.toChanges().videoId()).isEqualTo("video-1");
    }

    @Test
    @DisplayName("is prefilled from the lesson, with its video id when there is one")
    void prefilled() {
        LessonForm form = LessonForm.from(new VirtualLessonDetail("l-1", "Giros", "Texto", 15, true, 2, "video-3"));

        assertThat(form).extracting(LessonForm::getTitle, LessonForm::getDescription, LessonForm::getVideoId,
                LessonForm::getDurationMinutes, LessonForm::isFree)
                .containsExactly("Giros", "Texto", "video-3", "15", true);
        assertThat(errorsOf(form)).isEmpty();
        assertThat(LessonForm.from(new VirtualLessonDetail("l-2", "Giros", "Texto", 0, false, 1, null))
                .getVideoId()).isNull();
    }
}
