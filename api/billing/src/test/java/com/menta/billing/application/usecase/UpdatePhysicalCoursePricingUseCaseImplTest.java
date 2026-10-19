package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.application.dto.UpdatePhysicalCoursePricingCommand;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PhysicalCourseOwnershipPort;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.application.port.out.PhysicalCoursePricingRevisionRepository;
import com.menta.billing.domain.exception.InvalidIndividualSurchargeException;
import com.menta.billing.domain.exception.PhysicalCourseNotFoundException;
import com.menta.billing.domain.exception.PricingNotOwnedException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpdatePhysicalCoursePricingUseCaseImplTest {

    private static final String COURSE_ID = "course-1";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private PhysicalCourseOwnershipPort ownershipPort;
    private PhysicalCoursePricingRepository pricingRepository;
    private PhysicalCoursePricingRevisionRepository revisionRepository;
    private Clock clock;
    private UpdatePhysicalCoursePricingUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        ownershipPort = mock(PhysicalCourseOwnershipPort.class);
        pricingRepository = mock(PhysicalCoursePricingRepository.class);
        revisionRepository = mock(PhysicalCoursePricingRevisionRepository.class);
        clock = mock(Clock.class);
        when(clock.now()).thenReturn(NOW);
        useCase = new UpdatePhysicalCoursePricingUseCaseImpl(
            ownershipPort, pricingRepository, revisionRepository, clock
        );
    }

    private static UpdatePhysicalCoursePricingCommand command(String surcharge) {
        return new UpdatePhysicalCoursePricingCommand(
            new BigDecimal("150.00"), "ARS", new BigDecimal(surcharge), "ajuste de temporada"
        );
    }

    @Test
    void course_not_found_by_the_ownership_port_is_rejected_before_touching_pricing() {
        UUID actingUserId = UUID.randomUUID();
        when(ownershipPort.findProfessorId(COURSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.update(COURSE_ID, command("10"), actingUserId, false))
            .isInstanceOf(PhysicalCourseNotFoundException.class);
        verifyNoInteractions(pricingRepository, revisionRepository);
    }

    @Test
    void an_instructor_who_does_not_own_the_course_is_rejected() {
        UUID ownerId = UUID.randomUUID();
        UUID otherInstructorId = UUID.randomUUID();
        when(ownershipPort.findProfessorId(COURSE_ID)).thenReturn(Optional.of(ownerId));

        assertThatThrownBy(() -> useCase.update(COURSE_ID, command("10"), otherInstructorId, false))
            .isInstanceOf(PricingNotOwnedException.class);
        verifyNoInteractions(pricingRepository, revisionRepository);
    }

    @Test
    void an_admin_may_update_pricing_for_a_course_they_do_not_own() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(ownershipPort.findProfessorId(COURSE_ID)).thenReturn(Optional.of(ownerId));
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.empty());
        when(pricingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalCoursePricingResult result = useCase.update(COURSE_ID, command("10"), adminId, true);

        assertThat(result.version()).isEqualTo(1);
        verify(revisionRepository).append(
            eq(COURSE_ID), eq(adminId), eq("ajuste de temporada"), eq(1), isNull(), anyString(), eq(NOW)
        );
    }

    @Test
    void the_owning_instructor_publishes_the_first_version_with_no_previous_snapshot() {
        UUID professorId = UUID.randomUUID();
        when(ownershipPort.findProfessorId(COURSE_ID)).thenReturn(Optional.of(professorId));
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.empty());
        when(pricingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalCoursePricingResult result = useCase.update(COURSE_ID, command("10"), professorId, false);

        assertThat(result.courseId()).isEqualTo(COURSE_ID);
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.individualSurchargePercent()).isEqualByComparingTo("10");
        verify(revisionRepository).append(
            eq(COURSE_ID), eq(professorId), anyString(), eq(1), isNull(), anyString(), eq(NOW)
        );
    }

    @Test
    void updating_an_existing_pricing_increments_the_version_and_records_the_previous_snapshot() {
        UUID professorId = UUID.randomUUID();
        PhysicalCoursePricing existing = PhysicalCoursePricing.createFirstVersion(
            COURSE_ID, Money.of(new BigDecimal("100.00"), "ARS"), new BigDecimal("5"), NOW.minusSeconds(60)
        );
        when(ownershipPort.findProfessorId(COURSE_ID)).thenReturn(Optional.of(professorId));
        when(pricingRepository.findByCourseId(COURSE_ID)).thenReturn(Optional.of(existing));
        when(pricingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalCoursePricingResult result = useCase.update(COURSE_ID, command("10"), professorId, false);

        assertThat(result.version()).isEqualTo(2);
        verify(revisionRepository).append(
            eq(COURSE_ID), eq(professorId), anyString(), eq(2), anyString(), anyString(), eq(NOW)
        );
    }

    @Test
    void an_invalid_surcharge_is_rejected_before_saving_or_appending_a_revision() {
        UUID professorId = UUID.randomUUID();
        when(ownershipPort.findProfessorId(COURSE_ID)).thenReturn(Optional.of(professorId));

        assertThatThrownBy(() -> useCase.update(COURSE_ID, command("0"), professorId, false))
            .isInstanceOf(InvalidIndividualSurchargeException.class);

        verify(pricingRepository, never()).save(any());
        verifyNoInteractions(revisionRepository);
    }
}
