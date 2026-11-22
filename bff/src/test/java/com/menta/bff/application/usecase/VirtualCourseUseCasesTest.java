package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.CreateVirtualCourseCommand;
import com.menta.bff.application.dto.ManagedVirtualCourse;
import com.menta.bff.application.dto.VirtualCourseChanges;
import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.dto.VirtualCourseLevel;
import com.menta.bff.application.dto.VirtualCourseStatus;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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
}
