package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PhysicalCourseQuote;
import com.menta.billing.domain.model.PurchaseType;
import com.menta.billing.domain.model.QuoteAvailability;
import com.menta.billing.infrastructure.persistence.repository.PhysicalCourseQuoteJpaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhysicalCourseQuoteRepositoryAdapterTest {

    private PhysicalCourseQuoteJpaRepository jpaRepository;
    private PhysicalCourseQuoteRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(PhysicalCourseQuoteJpaRepository.class);
        adapter = new PhysicalCourseQuoteRepositoryAdapter(jpaRepository);
    }

    @Test
    void save_persists_and_returns_the_mapped_domain_object() {
        Instant now = Instant.now();
        PhysicalCourseQuote quote = PhysicalCourseQuote.reconstitute(
            java.util.UUID.randomUUID(), "course-1", PurchaseType.MONTHLY, Money.of(new BigDecimal("150.00"), "ARS"),
            new BigDecimal("10"), 1, 8, null, Money.of(new BigDecimal("150.00"), "ARS"), QuoteAvailability.AVAILABLE,
            now, now.plusSeconds(3600)
        );
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PhysicalCourseQuote result = adapter.save(quote);

        assertThat(result.getCourseId()).isEqualTo("course-1");
        assertThat(result.getPurchaseType()).isEqualTo(PurchaseType.MONTHLY);
        assertThat(result.getAmount()).isEqualTo(Money.of(new BigDecimal("150.00"), "ARS"));
    }
}
