package com.menta.billing.infrastructure.fulfillment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotImplementedPhysicalCapacityAssignmentPortTest {

    @Test
    void throws_until_a_real_adapter_replaces_it() {
        NotImplementedPhysicalCapacityAssignmentPort port = new NotImplementedPhysicalCapacityAssignmentPort();

        assertThatThrownBy(() -> port.assign("session-1", "purchase-1"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
