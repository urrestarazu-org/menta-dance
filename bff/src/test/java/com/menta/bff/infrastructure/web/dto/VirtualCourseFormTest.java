package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.dto.CreateVirtualCourseCommand;
import com.menta.bff.application.dto.VirtualCourseChanges;
import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.dto.VirtualCourseLevel;
import com.menta.bff.application.dto.VirtualCourseStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("VirtualCourseForm")
class VirtualCourseFormTest {

    private static final String URL = "https://img.example/";

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

    private static VirtualCourseForm valid() {
        VirtualCourseForm form = new VirtualCourseForm();
        form.setTitle("Tango base");
        form.setShortDescription("Corta");
        form.setDescription("Larga");
        form.setImageUrl(URL + "t.png");
        form.setCategory("Tango");
        form.setLevel("BEGINNER");
        return form;
    }

    /** Field name to message; each field declares one message per failure kind. */
    private static Map<String, String> errorsOf(VirtualCourseForm form) {
        Map<String, String> errors = new TreeMap<>();
        validator.validate(form).forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return errors;
    }

    static Stream<Arguments> limits() {
        BiConsumer<VirtualCourseForm, String> title = VirtualCourseForm::setTitle;
        BiConsumer<VirtualCourseForm, String> shortDescription = VirtualCourseForm::setShortDescription;
        BiConsumer<VirtualCourseForm, String> description = VirtualCourseForm::setDescription;
        BiConsumer<VirtualCourseForm, String> imageUrl = (form, value) -> form.setImageUrl(URL + value);
        BiConsumer<VirtualCourseForm, String> category = VirtualCourseForm::setCategory;
        return Stream.of(
                Arguments.of("title", title, 255, 0, "El título puede tener hasta 255 caracteres."),
                Arguments.of("shortDescription", shortDescription, 500, 0,
                        "La descripción corta puede tener hasta 500 caracteres."),
                Arguments.of("description", description, 5000, 0,
                        "La descripción puede tener hasta 5000 caracteres."),
                Arguments.of("imageUrl", imageUrl, 500, URL.length(),
                        "La URL de la imagen puede tener hasta 500 caracteres."),
                Arguments.of("category", category, 100, 0, "La categoría puede tener hasta 100 caracteres."));
    }

    @ParameterizedTest(name = "{0}: {2} characters pass, one more fails")
    @MethodSource("limits")
    void textLimits(String field, BiConsumer<VirtualCourseForm, String> setter, int max, int fixed, String message) {
        VirtualCourseForm atLimit = valid();
        setter.accept(atLimit, "x".repeat(max - fixed));
        VirtualCourseForm over = valid();
        setter.accept(over, "x".repeat(max - fixed + 1));

        assertThat(errorsOf(atLimit)).isEmpty();
        assertThat(errorsOf(over)).containsOnlyKeys(field).containsEntry(field, message);
    }

    @Test
    @DisplayName("an empty form reports every required field once")
    void emptyForm() {
        Map<String, String> errors = errorsOf(new VirtualCourseForm());

        assertThat(errors).containsOnlyKeys("title", "shortDescription", "description", "imageUrl", "category",
                "level").containsEntry("title", "Ingresá el título.").containsEntry("level", "Elegí un nivel.");
    }

    @ParameterizedTest(name = "blank \"{0}\" is refused ")
    @ValueSource(strings = {"", "   "})
    void blanksAreRefused(String blank) {
        VirtualCourseForm form = new VirtualCourseForm();
        form.setTitle(blank);
        form.setShortDescription(blank);
        form.setDescription(blank);
        form.setImageUrl(blank);
        form.setCategory(blank);
        form.setLevel(blank);

        assertThat(errorsOf(form)).containsOnlyKeys("title", "shortDescription", "description", "imageUrl",
                "category", "level");
    }

    @ParameterizedTest(name = "level \"{0}\" is refused")
    @ValueSource(strings = {"EXPERT", "beginner", "UNKNOWN"})
    void levelMustBeOneOfTheThree(String level) {
        VirtualCourseForm form = valid();
        form.setLevel(level);

        assertThat(errorsOf(form)).containsOnlyKeys("level");
    }

    @ParameterizedTest(name = "image URL \"{0}\" is refused")
    @ValueSource(strings = {"img.example/t.png", "ftp://img.example/t.png", "javascript:alert(1)", "https://",
        "https://a b"})
    void imageUrlMustBeHttp(String url) {
        VirtualCourseForm form = valid();
        form.setImageUrl(url);

        assertThat(errorsOf(form)).containsOnlyKeys("imageUrl")
                .containsEntry("imageUrl", "La URL de la imagen debe empezar con http:// o https://.");
    }

    @Test
    @DisplayName("builds the create command with the professor the controller decided")
    void createCommand() {
        VirtualCourseForm form = valid();
        form.setProfessorId("ignored");

        assertThat(form.toCommand("p-1")).isEqualTo(new CreateVirtualCourseCommand("Tango base", "Corta", "Larga",
                URL + "t.png", "Tango", VirtualCourseLevel.BEGINNER, "p-1"));
        assertThat(form.toCommand(null).professorId()).isNull();
    }

    @Test
    @DisplayName("builds the change with every field and the premium flag")
    void changes() {
        VirtualCourseForm form = valid();
        form.setPremium(true);

        assertThat(form.toChanges()).isEqualTo(new VirtualCourseChanges("Tango base", "Corta", "Larga", URL + "t.png",
                "Tango", VirtualCourseLevel.BEGINNER, true));
    }

    private static VirtualCourseDetail detail(VirtualCourseLevel level) {
        return new VirtualCourseDetail("c-1", "Vals", "Corta", "Larga", "p-1", URL + "v.png", "Vals", level, true,
                VirtualCourseStatus.PUBLISHED, List.of(), new VirtualCourseDetail.VirtualCourseStats(0, 0, "0m"));
    }

    @Test
    @DisplayName("is prefilled from the detail: thumbnail is the image, level by name, premium kept")
    void prefilled() {
        VirtualCourseForm form = VirtualCourseForm.from(detail(VirtualCourseLevel.ADVANCED));

        assertThat(form.getImageUrl()).isEqualTo(URL + "v.png");
        assertThat(form.getLevel()).isEqualTo("ADVANCED");
        assertThat(form.isPremium()).isTrue();
        assertThat(form.getProfessorId()).isNull();
        assertThat(errorsOf(form)).isEmpty();
    }

    @Test
    @DisplayName("an unknown level leaves the select on its placeholder")
    void unknownLevel() {
        assertThat(VirtualCourseForm.from(detail(VirtualCourseLevel.UNKNOWN)).getLevel()).isNull();
    }
}
