package com.menta.virtual.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.menta.virtual.infrastructure.persistence.entity.VirtualVideoAuditJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualVideoAuditJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VirtualVideoAuditRepositoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    private final VirtualVideoAuditJpaRepository jpaRepository = mock(VirtualVideoAuditJpaRepository.class);
    private final VirtualVideoAuditRepositoryAdapter adapter =
        new VirtualVideoAuditRepositoryAdapter(jpaRepository, () -> NOW);

    @Test
    void appends_a_row_with_the_given_fields_and_the_instant_of_the_clock() {
        UUID actorId = UUID.randomUUID();

        adapter.append("video-1", actorId, "RENAME_VIDEO", "title=Viejo", "title=Nuevo");

        ArgumentCaptor<VirtualVideoAuditJpaEntity> saved = ArgumentCaptor.forClass(VirtualVideoAuditJpaEntity.class);
        verify(jpaRepository).save(saved.capture());
        VirtualVideoAuditJpaEntity row = saved.getValue();
        assertThat(row.getId()).isNotNull();
        assertThat(row.getVideoId()).isEqualTo("video-1");
        assertThat(row.getActorId()).isEqualTo(actorId);
        assertThat(row.getAction()).isEqualTo("RENAME_VIDEO");
        assertThat(row.getPreviousValue()).isEqualTo("title=Viejo");
        assertThat(row.getNewValue()).isEqualTo("title=Nuevo");
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
    }
}
