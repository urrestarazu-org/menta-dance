package com.menta.physical.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.physical.infrastructure.web.dto.PhysicalDeviceListResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalDeviceResponse;
import java.lang.reflect.RecordComponent;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Type-level leak guard (#44, US-PHYSICAL-007, design C2/C10): {@link PhysicalDeviceView} is
 * returned by {@code get}/{@code list}/{@code revoke}, so it must never regain a secret or hash
 * component by a future edit. Reflective, not a hand assertion of the field list, so it fails the
 * moment any component name contains {@code secret} or {@code hash}.
 *
 * <p>Extended (P3, task 3.2) to cover the web-layer mirrors of that same guarantee:
 * {@link PhysicalDeviceResponse} and {@link PhysicalDeviceListResponse} (via its element type) —
 * these are the actual JSON bodies of {@code GET} and {@code revoke}, so the guard must hold there
 * too, not only on the application-layer DTO.</p>
 */
class PhysicalDeviceViewLeakGuardTest {

    @Test
    void declares_no_component_whose_name_contains_secret_or_hash() {
        assertNoLeakingComponent(PhysicalDeviceView.class);
    }

    @Test
    void physical_device_response_declares_no_component_whose_name_contains_secret_or_hash() {
        assertNoLeakingComponent(PhysicalDeviceResponse.class);
    }

    @Test
    void physical_device_list_response_wraps_a_list_of_the_secret_less_response_only() {
        RecordComponent[] components = PhysicalDeviceListResponse.class.getRecordComponents();
        assertThat(components).hasSize(1);
        assertThat(components[0].getName().toLowerCase(Locale.ROOT))
            .doesNotContain("secret").doesNotContain("hash");
    }

    private static void assertNoLeakingComponent(Class<?> recordType) {
        for (RecordComponent component : recordType.getRecordComponents()) {
            String name = component.getName().toLowerCase(Locale.ROOT);
            assertThat(name).doesNotContain("secret").doesNotContain("hash");
        }
    }
}
