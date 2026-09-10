package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.port.out.BillingApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetPlansViewUseCaseImpl}.
 * <p>
 * The load-bearing behavior is that this use case is a pure pass-through
 * (tasks.md 4.2): it delegates to {@link BillingApiClient#getPlans()} and
 * returns the result unchanged, with no try/catch — every exception the
 * client throws propagates untranslated.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetPlansViewUseCase")
class GetPlansViewUseCaseImplTest {

    @Mock
    private BillingApiClient billingApiClient;

    private GetPlansViewUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetPlansViewUseCaseImpl(billingApiClient);
    }

    @Test
    @DisplayName("delegates to billingApiClient.getPlans() and returns its result unchanged")
    void shouldReturnPlansUnchangedFromBillingApiClient() {
        List<PlanSummary> plans = List.of(
                new PlanSummary("plan-1", "Mensual", "Acceso mensual", BigDecimal.valueOf(1000), "ARS", 30, false),
                new PlanSummary("plan-2", "Anual", "Acceso anual", BigDecimal.valueOf(10000), "ARS", 365, true));
        when(billingApiClient.getPlans()).thenReturn(plans);

        List<PlanSummary> result = useCase.execute();

        assertThat(result).isSameAs(plans);
    }

    @Test
    @DisplayName("propagates NotFoundException untranslated")
    void shouldPropagateNotFoundExceptionUntranslated() {
        when(billingApiClient.getPlans()).thenThrow(new BillingApiClient.NotFoundException("no plans"));

        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(BillingApiClient.NotFoundException.class)
                .hasMessage("no plans");
    }

    @Test
    @DisplayName("propagates ServiceUnavailableException untranslated")
    void shouldPropagateServiceUnavailableExceptionUntranslated() {
        when(billingApiClient.getPlans()).thenThrow(new BillingApiClient.ServiceUnavailableException("unavailable"));

        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(BillingApiClient.ServiceUnavailableException.class)
                .hasMessage("unavailable");
    }
}
