package com.menta.physical.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Type-level leak guard (#44, US-PHYSICAL-007, design C2/C10): {@link PhysicalDeviceView} is
 * returned by {@code get}/{@code list}/{@code revoke}, so it must never regain a secret or hash
 * component by a future edit. Reflective, not a hand assertion of the field list, so it fails the
 * moment any component name contains {@code secret} or {@code hash}.
 */
class PhysicalDeviceViewLeakGuardTest {

    @Test
    void declares_no_component_whose_name_contains_secret_or_hash() {
        for (RecordComponent component : PhysicalDeviceView.class.getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            assertThat(name).doesNotContain("secret").doesNotContain("hash");
        }
    }
}
