package com.menta.virtual.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VirtualVideoAuditJpaEntityTest {

    @Test
    void exposes_every_field_through_its_getters() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        VirtualVideoAuditJpaEntity entity = new VirtualVideoAuditJpaEntity(
            id, "video-1", actorId, "DELETE_VIDEO", "videoId=video-1, title=t", null, createdAt
        );

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getVideoId()).isEqualTo("video-1");
        assertThat(entity.getActorId()).isEqualTo(actorId);
        assertThat(entity.getAction()).isEqualTo("DELETE_VIDEO");
        assertThat(entity.getPreviousValue()).isEqualTo("videoId=video-1, title=t");
        assertThat(entity.getNewValue()).isNull();
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }
}
