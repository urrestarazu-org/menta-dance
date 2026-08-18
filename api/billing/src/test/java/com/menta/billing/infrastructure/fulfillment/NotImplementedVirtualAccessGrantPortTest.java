package com.menta.billing.infrastructure.fulfillment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotImplementedVirtualAccessGrantPortTest {

    @Test
    void throws_until_a_real_adapter_replaces_it() {
        NotImplementedVirtualAccessGrantPort port = new NotImplementedVirtualAccessGrantPort();

        assertThatThrownBy(() -> port.grant("course-1", "subscription-1"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
