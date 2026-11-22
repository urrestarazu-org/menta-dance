package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.CreateVirtualCourseCommand;
import com.menta.bff.application.dto.CreateVirtualLessonCommand;
import com.menta.bff.application.dto.CreateVirtualModuleCommand;
import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.dto.ManagedVirtualLesson;
import com.menta.bff.application.dto.ManagedVirtualModule;
import com.menta.bff.application.dto.VirtualCourseChanges;
import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.dto.VirtualCourseLevel;
import com.menta.bff.application.dto.VirtualCourseStatus;
import com.menta.bff.application.dto.VirtualLessonChanges;
import com.menta.bff.application.dto.VirtualModuleChanges;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Virtual course management use cases")
class VirtualCourseUseCasesTest {

    private static final String TOKEN = "access-token-abc";
    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final ManagedVirtualCourse COURSE = new ManagedVirtualCourse(COURSE_ID, "Salsa", "Corta", "Larga",
            "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "https://img.test/a.png", "Salsa", VirtualCourseLevel.BEGINNER,
            false, VirtualCourseStatus.DRAFT);
    private static final VirtualCourseDetail DETAIL = new VirtualCourseDetail(COURSE_ID, "Salsa", "Corta", "Larga",
            "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "https://img.test/a.png", "Salsa", VirtualCourseLevel.BEGINNER,
            false, VirtualCourseStatus.DRAFT, List.of(), new VirtualCourseDetail.VirtualCourseStats(0, 0, "0m"));
    private static final CreateVirtualCourseCommand CREATION = new CreateVirtualCourseCommand("Salsa", "Corta", "Larga",
            "https://img.test/a.png", "Salsa", VirtualCourseLevel.BEGINNER, null);
    private static final VirtualCourseChanges CHANGES =
            new VirtualCourseChanges("Bachata", null, null, null, null, VirtualCourseLevel.ADVANCED, true);

    private static final String MODULE_ID = "7c8e9f00-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
    private static final String MODULE_2_ID = "8e9f0011-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
    private static final String LESSON_ID = "9f001122-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
    private static final ManagedVirtualModule MODULE = new ManagedVirtualModule(MODULE_ID, COURSE_ID, "Base", false, 1);
    private static final ManagedVirtualLesson LESSON =
            new ManagedVirtualLesson(LESSON_ID, MODULE_ID, COURSE_ID, "Paso", "Basico", null, 0, false, 1);
    private static final CreateVirtualModuleCommand NEW_MODULE = new CreateVirtualModuleCommand("Base", null, null);
    private static final VirtualModuleChanges MODULE_CHANGES = new VirtualModuleChanges(null, true, 2);
    private static final CreateVirtualLessonCommand NEW_LESSON =
            new CreateVirtualLessonCommand("Paso", "Basico", null, null, null, null);
    private static final VirtualLessonChanges LESSON_CHANGES =
            new VirtualLessonChanges("Paso 2", null, "v-1", 5, true, 0);

    @Mock
    private VirtualCourseManagementApiClient client;

    private ListManagedVirtualCoursesUseCase list;
    private GetManagedVirtualCourseUseCase get;
    private CreateVirtualCourseUseCase create;
    private UpdateVirtualCourseUseCase update;
    private PublishVirtualCourseUseCase publish;
    private UnpublishVirtualCourseUseCase unpublish;
    private DeleteVirtualCourseUseCase delete;

    @BeforeEach
    void setUp() {
        list = new ListManagedVirtualCoursesUseCaseImpl(client);
        get = new GetManagedVirtualCourseUseCaseImpl(client);
        create = new CreateVirtualCourseUseCaseImpl(client);
        update = new UpdateVirtualCourseUseCaseImpl(client);
        publish = new PublishVirtualCourseUseCaseImpl(client);
        unpublish = new UnpublishVirtualCourseUseCaseImpl(client);
        delete = new DeleteVirtualCourseUseCaseImpl(client);
    }

    @Test
    @DisplayName("every use case hands the API the token, the normalized id and the arguments as they are")
    void delegatesExactly() {
        when(client.getManagedCourse(TOKEN, COURSE_ID)).thenReturn(DETAIL);
        when(client.createCourse(TOKEN, CREATION)).thenReturn(COURSE);
        when(client.updateCourse(TOKEN, COURSE_ID, CHANGES)).thenReturn(COURSE);
        when(client.publishCourse(TOKEN, COURSE_ID)).thenReturn(COURSE);
        when(client.unpublishCourse(TOKEN, COURSE_ID)).thenReturn(COURSE);
        String messy = "  " + COURSE_ID.toUpperCase() + " ";

        assertThat(get.execute(TOKEN, messy)).isSameAs(DETAIL);
        assertThat(create.execute(TOKEN, CREATION)).isSameAs(COURSE);
        assertThat(update.execute(TOKEN, messy, CHANGES)).isSameAs(COURSE);
        assertThat(publish.execute(TOKEN, messy)).isSameAs(COURSE);
        assertThat(unpublish.execute(TOKEN, messy)).isSameAs(COURSE);
        delete.execute(TOKEN, messy);
        verify(client).deleteCourse(TOKEN, COURSE_ID);
    }

    @ParameterizedTest(name = "[{index}] id \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "../admin", "not-a-uuid"})
    @DisplayName("an id that is not a UUID is a course that does not exist, without calling the API")
    void invalidCourseId(String id) {
        List<Consumer<String>> calls = List.of(
                course -> get.execute(TOKEN, course),
                course -> update.execute(TOKEN, course, CHANGES),
                course -> publish.execute(TOKEN, course),
                course -> unpublish.execute(TOKEN, course),
                course -> delete.execute(TOKEN, course));

        for (Consumer<String> call : calls) {
            assertThatThrownBy(() -> call.accept(id)).isInstanceOfSatisfying(VirtualCourseManagementException.class,
                    e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_FOUND));
        }
        verifyNoInteractions(client);
    }

    @ParameterizedTest(name = "[{index}] token \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("without an access token nothing is sent: it reads as unavailable")
    void withoutToken(String token) {
        List<Consumer<String>> calls = List.of(
                t -> list.execute(t),
                t -> get.execute(t, COURSE_ID),
                t -> create.execute(t, CREATION),
                t -> update.execute(t, COURSE_ID, CHANGES),
                t -> publish.execute(t, COURSE_ID),
                t -> unpublish.execute(t, COURSE_ID),
                t -> delete.execute(t, COURSE_ID));

        for (Consumer<String> call : calls) {
            assertThatThrownBy(() -> call.accept(token)).isInstanceOf(ServiceUnavailableException.class);
        }
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("a missing command, changes or level is an invalid request, without calling the API")
    void nullsAreInvalidRequests() {
        CreateVirtualCourseCommand noLevel =
                new CreateVirtualCourseCommand("Salsa", "c", "l", null, "Salsa", null, null);

        for (Runnable call : List.<Runnable>of(
                () -> create.execute(TOKEN, null),
                () -> create.execute(TOKEN, noLevel),
                () -> update.execute(TOKEN, COURSE_ID, null))) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(VirtualCourseManagementException.class,
                    e -> assertThat(e.getCode()).isEqualTo(Code.INVALID_REQUEST));
        }
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("the refusals and the failures of the API reach the caller as they are")
    void failuresPropagate() {
        VirtualCourseManagementException refusal =
                new VirtualCourseManagementException(Code.COURSE_NOT_PUBLISHABLE, "Falta un video");
        ServiceUnavailableException down = new ServiceUnavailableException("down");
        when(client.publishCourse(TOKEN, COURSE_ID)).thenThrow(refusal);
        when(client.updateCourse(TOKEN, COURSE_ID, CHANGES))
                .thenThrow(new VirtualCourseManagementException(Code.COURSE_NOT_OWNED));
        when(client.getManagedCourse(TOKEN, COURSE_ID)).thenThrow(down);
        doThrow(new VirtualCourseManagementException(Code.COURSE_NOT_DRAFT))
                .when(client).deleteCourse(TOKEN, COURSE_ID);

        assertThatThrownBy(() -> publish.execute(TOKEN, COURSE_ID)).isSameAs(refusal);
        assertThatThrownBy(() -> get.execute(TOKEN, COURSE_ID)).isSameAs(down);
        assertThatThrownBy(() -> update.execute(TOKEN, COURSE_ID, CHANGES)).isInstanceOfSatisfying(
                VirtualCourseManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_OWNED));
        assertThatThrownBy(() -> delete.execute(TOKEN, COURSE_ID)).isInstanceOfSatisfying(
                VirtualCourseManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_DRAFT));
    }

    // ---- the structure: modules and lessons ----

    private CreateVirtualModuleUseCase createModule;
    private UpdateVirtualModuleUseCase updateModule;
    private ReorderVirtualModulesUseCase reorderModules;
    private CreateVirtualLessonUseCase createLesson;
    private UpdateVirtualLessonUseCase updateLesson;

    @BeforeEach
    void setUpStructure() {
        createModule = new CreateVirtualModuleUseCaseImpl(client);
        updateModule = new UpdateVirtualModuleUseCaseImpl(client);
        reorderModules = new ReorderVirtualModulesUseCaseImpl(client);
        createLesson = new CreateVirtualLessonUseCaseImpl(client);
        updateLesson = new UpdateVirtualLessonUseCaseImpl(client);
    }

    private static void assertRefusedWith(Runnable call, Code code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(VirtualCourseManagementException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test
    @DisplayName("the structure use cases hand the API the token, the normalized ids and the arguments as they are")
    void structureDelegatesExactly() {
        when(client.createModule(TOKEN, COURSE_ID, NEW_MODULE)).thenReturn(MODULE);
        when(client.updateModule(TOKEN, MODULE_ID, MODULE_CHANGES)).thenReturn(MODULE);
        when(client.reorderModules(TOKEN, COURSE_ID, List.of(MODULE_2_ID, MODULE_ID))).thenReturn(List.of(MODULE));
        when(client.createLesson(TOKEN, MODULE_ID, NEW_LESSON)).thenReturn(LESSON);
        when(client.updateLesson(TOKEN, LESSON_ID, LESSON_CHANGES)).thenReturn(LESSON);
        String messyCourse = "  " + COURSE_ID.toUpperCase() + " ";
        String messyModule = " " + MODULE_ID.toUpperCase();
        String messyLesson = LESSON_ID.toUpperCase() + "  ";

        assertThat(createModule.execute(TOKEN, messyCourse, NEW_MODULE)).isSameAs(MODULE);
        assertThat(updateModule.execute(TOKEN, messyModule, MODULE_CHANGES)).isSameAs(MODULE);
        assertThat(reorderModules.execute(TOKEN, messyCourse, List.of(" " + MODULE_2_ID.toUpperCase(), MODULE_ID)))
                .containsExactly(MODULE);
        assertThat(createLesson.execute(TOKEN, messyModule, NEW_LESSON)).isSameAs(LESSON);
        assertThat(updateLesson.execute(TOKEN, messyLesson, LESSON_CHANGES)).isSameAs(LESSON);
    }

    @ParameterizedTest(name = "[{index}] id \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "../admin", "not-a-uuid"})
    @DisplayName("an id that is not a UUID is a course, module or lesson that does not exist, without calling the API")
    void invalidStructureIds(String id) {
        assertRefusedWith(() -> createModule.execute(TOKEN, id, NEW_MODULE), Code.COURSE_NOT_FOUND);
        assertRefusedWith(() -> reorderModules.execute(TOKEN, id, List.of(MODULE_ID)), Code.COURSE_NOT_FOUND);
        assertRefusedWith(() -> updateModule.execute(TOKEN, id, MODULE_CHANGES), Code.MODULE_NOT_FOUND);
        assertRefusedWith(() -> createLesson.execute(TOKEN, id, NEW_LESSON), Code.MODULE_NOT_FOUND);
        assertRefusedWith(() -> updateLesson.execute(TOKEN, id, LESSON_CHANGES), Code.LESSON_NOT_FOUND);
        verifyNoInteractions(client);
    }

    static Stream<Arguments> invalidReorders() {
        return Stream.of(
                Arguments.of(null, Code.INVALID_REQUEST),
                Arguments.of(List.of(), Code.INVALID_REQUEST),
                Arguments.of(List.of(MODULE_ID, "not-a-uuid"), Code.INVALID_REQUEST),
                Arguments.of(Arrays.asList(MODULE_ID, null), Code.INVALID_REQUEST),
                Arguments.of(List.of(MODULE_ID, MODULE_2_ID, MODULE_ID), Code.MODULE_REORDER_MISMATCH),
                Arguments.of(List.of(MODULE_ID, " " + MODULE_ID.toUpperCase()), Code.MODULE_REORDER_MISMATCH));
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("invalidReorders")
    @DisplayName("a reorder that is empty, has a malformed id or repeats one never reaches the API")
    void invalidReorder(List<String> moduleIds, Code code) {
        assertRefusedWith(() -> reorderModules.execute(TOKEN, COURSE_ID, moduleIds), code);
        verifyNoInteractions(client);
    }

    @ParameterizedTest(name = "[{index}] token \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("without an access token no structure call is sent: it reads as unavailable")
    void structureWithoutToken(String token) {
        List<Consumer<String>> calls = List.of(
                t -> createModule.execute(t, COURSE_ID, NEW_MODULE),
                t -> updateModule.execute(t, MODULE_ID, MODULE_CHANGES),
                t -> reorderModules.execute(t, COURSE_ID, List.of(MODULE_ID)),
                t -> createLesson.execute(t, MODULE_ID, NEW_LESSON),
                t -> updateLesson.execute(t, LESSON_ID, LESSON_CHANGES));

        for (Consumer<String> call : calls) {
            assertThatThrownBy(() -> call.accept(token)).isInstanceOf(ServiceUnavailableException.class);
        }
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("a missing command or changes is an invalid request, without calling the API")
    void missingStructureArgumentsAreInvalidRequests() {
        assertRefusedWith(() -> createModule.execute(TOKEN, COURSE_ID, null), Code.INVALID_REQUEST);
        assertRefusedWith(() -> updateModule.execute(TOKEN, MODULE_ID, null), Code.INVALID_REQUEST);
        assertRefusedWith(() -> createLesson.execute(TOKEN, MODULE_ID, null), Code.INVALID_REQUEST);
        assertRefusedWith(() -> updateLesson.execute(TOKEN, LESSON_ID, null), Code.INVALID_REQUEST);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("the refusals and the failures of the API reach the structure callers as they are")
    void structureFailuresPropagate() {
        VirtualCourseManagementException mismatch =
                new VirtualCourseManagementException(Code.MODULE_REORDER_MISMATCH);
        ServiceUnavailableException down = new ServiceUnavailableException("down");
        when(client.reorderModules(TOKEN, COURSE_ID, List.of(MODULE_ID))).thenThrow(mismatch);
        when(client.updateLesson(TOKEN, LESSON_ID, LESSON_CHANGES)).thenThrow(down);
        when(client.createModule(TOKEN, COURSE_ID, NEW_MODULE))
                .thenThrow(new VirtualCourseManagementException(Code.COURSE_NOT_OWNED));

        assertThatThrownBy(() -> reorderModules.execute(TOKEN, COURSE_ID, List.of(MODULE_ID))).isSameAs(mismatch);
        assertThatThrownBy(() -> updateLesson.execute(TOKEN, LESSON_ID, LESSON_CHANGES)).isSameAs(down);
        assertRefusedWith(() -> createModule.execute(TOKEN, COURSE_ID, NEW_MODULE), Code.COURSE_NOT_OWNED);
    }
}
