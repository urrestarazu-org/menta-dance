package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PlanDetailResult;
import com.menta.billing.application.dto.RateLimitDecision;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.domain.exception.PlanNotFoundException;
import com.menta.billing.domain.exception.PlanRateLimitedException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetPlanUseCaseImplTest {

    private static final String CLIENT_FINGERPRINT = "a".repeat(64);

    private PlanRepository planRepository;
    private CourseCatalogPort courseCatalogPort;
    private BillingPlansRateLimitPort rateLimitPort;
    private GetPlanUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        courseCatalogPort = mock(CourseCatalogPort.class);
        rateLimitPort = mock(BillingPlansRateLimitPort.class);
        useCase = new GetPlanUseCaseImpl(planRepository, courseCatalogPort, rateLimitPort);
    }

    private void allowRateLimit() {
        when(rateLimitPort.consume(any())).thenReturn(RateLimitDecision.allowed());
    }

    private static Plan plan(PlanId id) {
        return new Plan(
            id, "Plan Mensual", "desc", Money.of(BigDecimal.TEN, "ARS"), 30,
            true, PlanStatus.ACTIVE, "terms", "cancellation", List.of()
        );
    }

    @Test
    void returns_the_full_detail_of_an_active_plan() {
        allowRateLimit();
        PlanId id = PlanId.generate();
        when(planRepository.findActiveById(id)).thenReturn(Optional.of(plan(id)));
        when(courseCatalogPort.courseName(any())).thenReturn(Optional.empty());

        PlanDetailResult result = useCase.getPlan(id.toString(), CLIENT_FINGERPRINT);

        assertThat(result.id()).isEqualTo(id.toString());
        assertThat(result.termsAndConditions()).isEqualTo("terms");
        assertThat(result.cancellationPolicy()).isEqualTo("cancellation");
    }

    @Test
    void an_id_the_repository_does_not_return_as_active_is_not_found() {
        allowRateLimit();
        PlanId id = PlanId.generate();
        when(planRepository.findActiveById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getPlan(id.toString(), CLIENT_FINGERPRINT))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void a_malformed_id_is_not_found_rather_than_a_validation_error() {
        // Same anti-enumeration discipline as auth: a malformed id must not
        // be distinguishable from a well-formed one that does not exist.
        allowRateLimit();

        assertThatThrownBy(() -> useCase.getPlan("not-a-uuid", CLIENT_FINGERPRINT))
            .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void an_exhausted_budget_rejects_before_touching_the_repository() {
        when(rateLimitPort.consume(CLIENT_FINGERPRINT))
            .thenReturn(RateLimitDecision.limited(Duration.ofSeconds(9)));

        assertThatThrownBy(() -> useCase.getPlan(PlanId.generate().toString(), CLIENT_FINGERPRINT))
            .isInstanceOf(PlanRateLimitedException.class)
            .extracting(ex -> ((PlanRateLimitedException) ex).getRetryAfter())
            .isEqualTo(Duration.ofSeconds(9));
        verifyNoInteractions(planRepository);
    }
}
