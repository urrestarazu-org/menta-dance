package com.menta.bff.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.application.dto.CreateVirtualModuleCommand;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualModuleDetail;
import com.menta.bff.application.dto.VirtualModuleChanges;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("ModuleForm")
class ModuleFormTest {

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

    private static ModuleForm form(String title) {
        ModuleForm form = new ModuleForm();
        form.setTitle(title);
        return form;
    }

    private static Map<String, String> errorsOf(ModuleForm form) {
        Map<String, String> errors = new TreeMap<>();
        validator.validate(form).forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return errors;
    }

    @Test
    @DisplayName("a title of 255 characters passes and one more fails with its message")
    void titleLimit() {
        assertThat(errorsOf(form("x".repeat(255)))).isEmpty();
        assertThat(errorsOf(form("x".repeat(256)))).containsOnlyKeys("title")
                .containsEntry("title", "El título puede tener hasta 255 caracteres.");
    }

    @ParameterizedTest(name = "title \"{0}\" is refused")
    @ValueSource(strings = {"", "   "})
    @DisplayName("a missing or blank title reports only the required message")
    void blankTitle(String blank) {
        assertThat(errorsOf(form(blank))).containsOnlyKeys("title").containsEntry("title", "Ingresá el título.");
        assertThat(errorsOf(new ModuleForm())).containsOnlyKeys("title");
    }

    @Test
    @DisplayName("builds the create command without an order and the changes with title and preview")
    void commandAndChanges() {
        ModuleForm form = form("Pasos básicos");
        form.setPreview(true);

        assertThat(form.toCommand()).isEqualTo(new CreateVirtualModuleCommand("Pasos básicos", true, null));
        assertThat(form.toChanges()).isEqualTo(new VirtualModuleChanges("Pasos básicos", true, null));
    }

    @Test
    @DisplayName("prefills from a module of the detail")
    void prefill() {
        ModuleForm form = ModuleForm.from(new VirtualModuleDetail("m-1", "Giros", true, 2, List.of()));

        assertThat(form.getTitle()).isEqualTo("Giros");
        assertThat(form.isPreview()).isTrue();
    }
}
