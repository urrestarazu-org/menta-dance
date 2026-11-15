package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.bff.application.dto.InProgressCourse;
import com.menta.bff.application.port.out.VirtualApiClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link GetContinueLearningUseCaseImpl}: the "Continuar" section of a student's
 * dashboard (#363), read with the caller's token and never failing the page.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetContinueLearningUseCase")
class GetContinueLearningUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final int LIMIT = GetContinueLearningUseCaseImpl.DASHBOARD_LIMIT;

    @Mock
    private VirtualApiClient virtualApiClient;

    private GetContinueLearningUseCase useCase;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        useCase = new GetContinueLearningUseCaseImpl(virtualApiClient);
        logger = (Logger) LoggerFactory.getLogger(GetContinueLearningUseCaseImpl.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    @DisplayName("the dashboard asks for a short list, not the API default of ten")
    void dashboardLimitIsFive() {
        assertThat(LIMIT).isEqualTo(5);
    }

    @Test
    @DisplayName("the courses in progress are shown in the order the API returns them")
    void coursesInProgress() {
        List<InProgressCourse> courses = List.of(
                new InProgressCourse("c-2", "Bachata", 1, 4, 25, "l-21", "Paso básico"),
                new InProgressCourse("c-1", "Salsa", 3, 10, 30, "l-13", "Giros"));
        when(virtualApiClient.listInProgressCourses(TOKEN, LIMIT)).thenReturn(courses);

        ContinueLearningView view = useCase.execute(TOKEN);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.empty()).isFalse();
        assertThat(view.courses()).containsExactlyElementsOf(courses);
    }

    @Test
    @DisplayName("no course in progress is an empty section, not a failure")
    void noCourseInProgress() {
        when(virtualApiClient.listInProgressCourses(TOKEN, LIMIT)).thenReturn(List.of());

        ContinueLearningView view = useCase.execute(TOKEN);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.empty()).isTrue();
        assertThat(view.courses()).isEmpty();
    }

    @Test
    @DisplayName("an unavailable Virtual API becomes the unavailable notice and logs no token")
    void virtualApiUnavailable() {
        when(virtualApiClient.listInProgressCourses(TOKEN, LIMIT))
                .thenThrow(new VirtualApiClient.ServiceUnavailableException("Virtual API unavailable"));

        ContinueLearningView view = useCase.execute(TOKEN);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.empty()).isFalse();
        assertThat(view.courses()).isEmpty();
        assertThat(logs.list).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage())
                        .contains("ServiceUnavailableException").doesNotContain(TOKEN));
    }

    @ParameterizedTest(name = "a token \"{0}\" fails closed to the unavailable notice without calling the API")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void missingTokenIsUnavailableWithoutCall(String token) {
        ContinueLearningView view = useCase.execute(token);

        assertThat(view.unavailable()).isTrue();
        verifyNoInteractions(virtualApiClient);
    }
}
