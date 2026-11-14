package com.menta.bff.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UnavailableInstructorDisplayNameSource")
class UnavailableInstructorDisplayNameSourceTest {

    @Test
    @DisplayName("knows no name yet: the access token holds none and there is no current-user endpoint")
    void knowsNoName() {
        assertThat(new UnavailableInstructorDisplayNameSource().displayNameOf("access-token-abc")).isEmpty();
    }
}
