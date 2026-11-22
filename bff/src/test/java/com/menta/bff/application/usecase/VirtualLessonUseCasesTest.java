package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.ManagedVirtualLesson;
import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualLessonDetail;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualModuleDetail;
import com.menta.bff.application.dto.VirtualCourseLevel;
import com.menta.bff.application.dto.VirtualCourseStatus;
import com.menta.bff.application.dto.VirtualLessonChanges;
import com.menta.bff.application.dto.VirtualLessonLocation;
import com.menta.bff.application.dto.VirtualLessonPosition;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Virtual lesson use cases: find, swap and delete")
class VirtualLessonUseCasesTest {

    private static final String TOKEN = "access-token-abc";
    private static final String LESSON_ID = "9f001122-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
    private static final String NEIGHBOUR_ID = "aa112233-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
    private static final VirtualLessonPosition LESSON = new VirtualLessonPosition(LESSON_ID, 1);
    private static final VirtualLessonPosition NEIGHBOUR = new VirtualLessonPosition(NEIGHBOUR_ID, 2);
    private static final ManagedVirtualLesson MANAGED =
            new ManagedVirtualLesson(LESSON_ID, "m", "c", "Paso", "Basico", null, 0, false, 2);

    @Mock
    private VirtualCourseManagementApiClient client;

    private static final String COURSE_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String MODULE_ID = "7c8e9f00-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
    private static final VirtualLessonDetail LESSON_DETAIL =
            new VirtualLessonDetail(LESSON_ID, "Paso", "Basico", 10, true, 0, null);
    private static final VirtualCourseDetail DETAIL = new VirtualCourseDetail(COURSE_ID, "Salsa", "Corta", "Larga",
            "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00", "https://img.test/a.png", "Salsa", VirtualCourseLevel.BEGINNER,
            false, VirtualCourseStatus.DRAFT, List.of(
                    new VirtualModuleDetail("otro", "Otro", false, 1, List.of()),
                    new VirtualModuleDetail(MODULE_ID, "Base", false, 2, List.of(LESSON_DETAIL))),
            new VirtualCourseDetail.VirtualCourseStats(2, 1, "10m"));

    private SwapVirtualLessonsUseCase swap;
    private GetManagedVirtualLessonUseCase find;
    private DeleteVirtualLessonUseCase delete;

    @BeforeEach
    void setUp() {
        swap = new SwapVirtualLessonsUseCaseImpl(client);
        delete = new DeleteVirtualLessonUseCaseImpl(client);
        find = new GetManagedVirtualLessonUseCaseImpl(client);
    }

    private static VirtualLessonChanges order(int order) {
        return new VirtualLessonChanges(null, null, null, null, null, order);
    }

    // ---- find ----

    @Test
    @DisplayName("find reads the course with the normalized id and answers the lesson with its module")
    void findLesson() {
        when(client.getManagedCourse(TOKEN, COURSE_ID)).thenReturn(DETAIL);

        VirtualLessonLocation found = find.execute(TOKEN, COURSE_ID.toUpperCase(), " " + MODULE_ID, LESSON_ID);

        assertThat(found).isEqualTo(new VirtualLessonLocation(COURSE_ID, MODULE_ID, "Base", LESSON_DETAIL));
    }

    @Test
    @DisplayName("find of a module that is not in the course, or a lesson that is not in the module, says which")
    void findMissing() {
        when(client.getManagedCourse(TOKEN, COURSE_ID)).thenReturn(DETAIL);
        String otherModule = "8e9f0011-1d2a-4e3b-9c4d-5b6f0a1b2c3d";
        Object[][] cases = {{otherModule, LESSON_ID, Code.MODULE_NOT_FOUND},
            {MODULE_ID, NEIGHBOUR_ID, Code.LESSON_NOT_FOUND}, {"otro", LESSON_ID, Code.MODULE_NOT_FOUND}};

        for (Object[] c : cases) {
            assertThatThrownBy(() -> find.execute(TOKEN, COURSE_ID, (String) c[0], (String) c[1]))
                    .isInstanceOfSatisfying(VirtualCourseManagementException.class,
                            e -> assertThat(e.getCode()).isEqualTo(c[2]));
        }
    }

    @Test
    @DisplayName("find with an id that is not a UUID is not found by that id, without calling the API")
    void findInvalidIds() {
        Object[][] cases = {{"nope", MODULE_ID, LESSON_ID, Code.COURSE_NOT_FOUND},
            {COURSE_ID, "nope", LESSON_ID, Code.MODULE_NOT_FOUND}, {COURSE_ID, MODULE_ID, null, Code.LESSON_NOT_FOUND}};

        for (Object[] c : cases) {
            assertThatThrownBy(() -> find.execute(TOKEN, (String) c[0], (String) c[1], (String) c[2]))
                    .isInstanceOfSatisfying(VirtualCourseManagementException.class,
                            e -> assertThat(e.getCode()).isEqualTo(c[3]));
        }

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("find without an access token is unavailable, without calling the API")
    void findWithoutToken() {
        assertThatThrownBy(() -> find.execute(" ", COURSE_ID, MODULE_ID, LESSON_ID))
                .isInstanceOf(ServiceUnavailableException.class);

        verifyNoInteractions(client);
    }

    // ---- delete ----

    @Test
    @DisplayName("delete hands the API the token and the normalized lesson id")
    void deleteDelegates() {
        delete.execute(TOKEN, "  " + LESSON_ID.toUpperCase() + " ");

        verify(client).deleteLesson(TOKEN, LESSON_ID);
    }

    @ParameterizedTest(name = "[{index}] id \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "../admin", "not-a-uuid"})
    @DisplayName("delete of an id that is not a UUID is a lesson that does not exist, without calling the API")
    void deleteInvalidId(String id) {
        assertThatThrownBy(() -> delete.execute(TOKEN, id)).isInstanceOfSatisfying(
                VirtualCourseManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.LESSON_NOT_FOUND));

        verifyNoInteractions(client);
    }

    @ParameterizedTest(name = "[{index}] token \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("delete without an access token is unavailable, without calling the API")
    void deleteWithoutToken(String token) {
        assertThatThrownBy(() -> delete.execute(token, LESSON_ID)).isInstanceOf(ServiceUnavailableException.class);

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("what the API refuses on delete reaches the caller unchanged")
    void deleteRefusal() {
        doThrow(new VirtualCourseManagementException(Code.COURSE_NOT_OWNED)).when(client)
                .deleteLesson(TOKEN, LESSON_ID);

        assertThatThrownBy(() -> delete.execute(TOKEN, LESSON_ID)).isInstanceOfSatisfying(
                VirtualCourseManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_OWNED));
    }

    // ---- swap ----

    @Test
    @DisplayName("swap sends each lesson the order of the other, the one that moves first, with only the order")
    void swapSendsBothOrders() {
        when(client.updateLesson(any(), any(), any())).thenReturn(MANAGED);

        swap.execute(TOKEN, new VirtualLessonPosition(LESSON_ID.toUpperCase(), 1), NEIGHBOUR);

        InOrder calls = inOrder(client);
        calls.verify(client).updateLesson(TOKEN, LESSON_ID, order(2));
        calls.verify(client).updateLesson(TOKEN, NEIGHBOUR_ID, order(1));
        verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("swap that fails on the first update stops there: nothing to undo")
    void swapFirstUpdateFails() {
        when(client.updateLesson(TOKEN, LESSON_ID, order(2)))
                .thenThrow(new VirtualCourseManagementException(Code.COURSE_NOT_OWNED));

        assertThatThrownBy(() -> swap.execute(TOKEN, LESSON, NEIGHBOUR)).isInstanceOfSatisfying(
                VirtualCourseManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.COURSE_NOT_OWNED));

        verify(client).updateLesson(TOKEN, LESSON_ID, order(2));
        verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("swap that fails on the second update puts the first lesson back and reports the original failure")
    void swapSecondUpdateFailsAndIsUndone() {
        when(client.updateLesson(any(), any(), any())).thenReturn(MANAGED);
        when(client.updateLesson(TOKEN, NEIGHBOUR_ID, order(1)))
                .thenThrow(new VirtualCourseManagementException(Code.LESSON_NOT_FOUND));

        assertThatThrownBy(() -> swap.execute(TOKEN, LESSON, NEIGHBOUR)).isInstanceOfSatisfying(
                VirtualCourseManagementException.class, e -> assertThat(e.getCode()).isEqualTo(Code.LESSON_NOT_FOUND));

        InOrder calls = inOrder(client);
        calls.verify(client).updateLesson(TOKEN, LESSON_ID, order(2));
        calls.verify(client).updateLesson(TOKEN, NEIGHBOUR_ID, order(1));
        calls.verify(client).updateLesson(TOKEN, LESSON_ID, order(1));
        verifyNoMoreInteractions(client);
    }

    @Test
    @DisplayName("when putting the first lesson back fails too, the failure of the second update is the one reported")
    void swapUndoFailsToo() {
        when(client.updateLesson(TOKEN, LESSON_ID, order(2))).thenReturn(MANAGED);
        when(client.updateLesson(TOKEN, NEIGHBOUR_ID, order(1)))
                .thenThrow(new ServiceUnavailableException("second"));
        when(client.updateLesson(TOKEN, LESSON_ID, order(1))).thenThrow(new ServiceUnavailableException("undo"));

        assertThatThrownBy(() -> swap.execute(TOKEN, LESSON, NEIGHBOUR))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage("second");
    }

    @ParameterizedTest(name = "[{index}] id \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-uuid"})
    @DisplayName("swap with an id that is not a UUID is a lesson that does not exist, without calling the API")
    void swapInvalidIds(String id) {
        VirtualLessonPosition bad = new VirtualLessonPosition(id, 3);
        for (Runnable call : new Runnable[] {() -> swap.execute(TOKEN, bad, NEIGHBOUR),
            () -> swap.execute(TOKEN, LESSON, bad)}) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(VirtualCourseManagementException.class,
                    e -> assertThat(e.getCode()).isEqualTo(Code.LESSON_NOT_FOUND));
        }

        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("swap of a lesson with itself, or without positions, is an invalid request without calling the API")
    void swapInvalidRequest() {
        VirtualLessonPosition same = new VirtualLessonPosition(LESSON_ID.toUpperCase(), 5);
        for (Runnable call : new Runnable[] {() -> swap.execute(TOKEN, LESSON, same),
            () -> swap.execute(TOKEN, null, NEIGHBOUR), () -> swap.execute(TOKEN, LESSON, null)}) {
            assertThatThrownBy(call::run).isInstanceOfSatisfying(VirtualCourseManagementException.class,
                    e -> assertThat(e.getCode()).isEqualTo(Code.INVALID_REQUEST));
        }

        verifyNoInteractions(client);
    }

    @ParameterizedTest(name = "[{index}] token \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("swap without an access token is unavailable, without calling the API")
    void swapWithoutToken(String token) {
        assertThatThrownBy(() -> swap.execute(token, LESSON, NEIGHBOUR))
                .isInstanceOf(ServiceUnavailableException.class);

        verifyNoInteractions(client);
    }
}
