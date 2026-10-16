package com.menta.app.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.billing.domain.model.PurchaseType;
import com.menta.shared.physical.AssignmentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AssignmentTypeMapperTest {

    @ParameterizedTest
    @EnumSource(PurchaseType.class)
    @DisplayName("Every PurchaseType constant maps to the AssignmentType with the same name")
    void every_purchase_type_maps_to_the_assignment_type_of_the_same_name(PurchaseType purchaseType) {
        AssignmentType mapped = AssignmentTypeMapper.from(purchaseType);

        assertThat(mapped.name()).isEqualTo(purchaseType.name());
    }
}
