package com.menta.billing.infrastructure.config;

import com.menta.billing.application.port.in.GetPlanUseCase;
import com.menta.billing.application.port.in.ListPlansUseCase;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.usecase.GetPlanUseCaseImpl;
import com.menta.billing.application.usecase.ListPlansUseCaseImpl;
import com.menta.billing.infrastructure.security.RedisBillingPlansRateLimitPort;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Wires the plans use cases. Adapter classes ({@code PlanRepositoryAdapter},
 * {@code NotImplementedCourseCatalogPort}) are {@code @Component}-scanned;
 * the use cases are plain Java classes composed here, mirroring {@code
 * AuthConfiguration}'s rationale: calling use cases directly from
 * controllers keeps port dependencies visible at the boundary instead of
 * implicit {@code @Autowired} on use-case classes.
 */
@Configuration
public class BillingConfiguration {

    @Bean
    public BillingPlansRateLimitPort billingPlansRateLimitPort(
        RedisTemplate<String, String> redisTemplate,
        @Value("${billing.plans.rate-limit.max-requests:60}") long maxRequests,
        @Value("${billing.plans.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        return new RedisBillingPlansRateLimitPort(redisTemplate, maxRequests, Duration.ofSeconds(windowSeconds));
    }

    @Bean
    public ListPlansUseCase listPlansUseCase(
        PlanRepository planRepository, CourseCatalogPort courseCatalogPort,
        BillingPlansRateLimitPort billingPlansRateLimitPort
    ) {
        return new ListPlansUseCaseImpl(planRepository, courseCatalogPort, billingPlansRateLimitPort);
    }

    @Bean
    public GetPlanUseCase getPlanUseCase(
        PlanRepository planRepository, CourseCatalogPort courseCatalogPort,
        BillingPlansRateLimitPort billingPlansRateLimitPort
    ) {
        return new GetPlanUseCaseImpl(planRepository, courseCatalogPort, billingPlansRateLimitPort);
    }
}
