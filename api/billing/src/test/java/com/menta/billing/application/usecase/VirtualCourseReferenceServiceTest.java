package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.application.port.out.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VirtualCourseReferenceServiceTest {

    private static final String COURSE_ID = "9d3f2c1a-0000-4000-8000-000000000001";

    private PlanRepository planRepository;
    private SubscriptionRepository subscriptionRepository;
    private VirtualCourseReferenceService service;

    @BeforeEach
    void setUp() {
        planRepository = mock(PlanRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        service = new VirtualCourseReferenceService(planRepository, subscriptionRepository);
    }

    @Test
    void a_course_included_in_a_plan_is_referenced_without_asking_the_subscriptions() {
        when(planRepository.existsByCourseId(COURSE_ID)).thenReturn(true);

        assertThat(service.isReferenced(COURSE_ID)).isTrue();

        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void a_course_only_in_a_subscription_snapshot_is_referenced() {
        when(planRepository.existsByCourseId(COURSE_ID)).thenReturn(false);
        when(subscriptionRepository.existsByCourseId(COURSE_ID)).thenReturn(true);

        assertThat(service.isReferenced(COURSE_ID)).isTrue();
    }

    @Test
    void a_course_nothing_references_is_not_referenced() {
        when(planRepository.existsByCourseId(COURSE_ID)).thenReturn(false);
        when(subscriptionRepository.existsByCourseId(COURSE_ID)).thenReturn(false);

        assertThat(service.isReferenced(COURSE_ID)).isFalse();

        verify(planRepository).existsByCourseId(COURSE_ID);
        verify(subscriptionRepository).existsByCourseId(COURSE_ID);
    }

    @Test
    void a_blank_or_null_course_id_is_not_referenced_and_queries_nothing() {
        assertThat(service.isReferenced(null)).isFalse();
        assertThat(service.isReferenced(" ")).isFalse();

        verifyNoInteractions(planRepository, subscriptionRepository);
    }
}
