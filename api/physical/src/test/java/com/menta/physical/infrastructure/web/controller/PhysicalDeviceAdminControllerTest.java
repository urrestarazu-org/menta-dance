package com.menta.physical.infrastructure.web.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.port.in.GetPhysicalDeviceUseCase;
import com.menta.physical.application.port.in.ListPhysicalDevicesUseCase;
import com.menta.physical.application.port.in.RegisterPhysicalDeviceUseCase;
import com.menta.physical.application.port.in.RevokePhysicalDeviceUseCase;
import com.menta.physical.application.port.in.RotatePhysicalDeviceSecretUseCase;
import com.menta.physical.domain.exception.DeviceAlreadyRevokedException;
import com.menta.physical.domain.exception.DeviceNotFoundException;
import com.menta.physical.domain.exception.DeviceRevokedException;
import com.menta.physical.domain.model.DeviceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MockMvc slice for the device-registry admin endpoints (#44, US-PHYSICAL-007, design C6),
 * mirrors {@code PhysicalAttendanceAdminControllerTest}/{@code PhysicalCourseAdminControllerTest}.
 * {@code SecurityConfig} is responsible for the coarse role gate (C7), not this controller.
 */
class PhysicalDeviceAdminControllerTest {

    private RegisterPhysicalDeviceUseCase registerUseCase;
    private GetPhysicalDeviceUseCase getUseCase;
    private RotatePhysicalDeviceSecretUseCase rotateUseCase;
    private RevokePhysicalDeviceUseCase revokeUseCase;
    private ListPhysicalDevicesUseCase listUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        registerUseCase = mock(RegisterPhysicalDeviceUseCase.class);
        getUseCase = mock(GetPhysicalDeviceUseCase.class);
        rotateUseCase = mock(RotatePhysicalDeviceSecretUseCase.class);
        revokeUseCase = mock(RevokePhysicalDeviceUseCase.class);
        listUseCase = mock(ListPhysicalDevicesUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PhysicalDeviceAdminController(
                registerUseCase, getUseCase, rotateUseCase, revokeUseCase, listUseCase
            ))
            .setControllerAdvice(new PhysicalDeviceExceptionHandler())
            .build();
    }

    private static Authentication authOf(UUID userId, String role) {
        return new UsernamePasswordAuthenticationToken(
            userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private static PhysicalDeviceView view(UUID id, DeviceStatus status) {
        Instant now = Instant.parse("2026-09-25T10:00:00Z");
        return new PhysicalDeviceView(id, "Puerta principal", "Sede Centro", status, null, now, now);
    }

    @Test
    void register_returns_201_with_the_raw_secret() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        PhysicalDeviceSecretResult result =
            new PhysicalDeviceSecretResult(view(deviceId, DeviceStatus.ACTIVE), "raw-secret-value");
        when(registerUseCase.register(any(), eq(adminId))).thenReturn(result);

        mockMvc.perform(post("/api/v1/admin/physical/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Puerta principal\",\"location\":\"Sede Centro\"}")
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.secret", is("raw-secret-value")))
            .andExpect(jsonPath("$.device.id", is(deviceId.toString())))
            .andExpect(jsonPath("$.device.status", is("ACTIVE")));

        verify(registerUseCase).register(any(), eq(adminId));
    }

    @Test
    void register_echoes_the_optional_expiresAt_as_inert_metadata() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2027-01-15T00:00:00Z");
        PhysicalDeviceView view = new PhysicalDeviceView(
            deviceId, "Puerta principal", "Sede Centro", DeviceStatus.ACTIVE, expiresAt,
            Instant.parse("2026-09-25T10:00:00Z"), Instant.parse("2026-09-25T10:00:00Z")
        );
        PhysicalDeviceSecretResult result =
            new PhysicalDeviceSecretResult(view, "raw-secret-value");
        when(registerUseCase.register(any(), eq(adminId))).thenReturn(result);

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/admin/physical/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Puerta principal\",\"location\":\"Sede Centro\","
                        + "\"expiresAt\":\"" + expiresAt + "\"}"
                )
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isCreated())
            .andReturn();

        // This standalone MockMvc slice uses Jackson's default WRITE_DATES_AS_TIMESTAMPS (no
        // Spring Boot JacksonAutoConfiguration here), so Instant serializes as epoch seconds
        // rather than ISO-8601 — read it back as a number instead of asserting a literal string.
        Number expiresAtInResponse =
            JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.device.expiresAt");
        assertEquals(expiresAt.getEpochSecond(), expiresAtInResponse.longValue());

        verify(registerUseCase).register(
            argThat(command -> expiresAt.equals(command.expiresAt())), eq(adminId)
        );
    }

    @Test
    void register_rejects_a_blank_name_with_400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/physical/devices")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"location\":\"Sede Centro\"}")
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    @Test
    void get_returns_200_with_no_secret_field() throws Exception {
        UUID deviceId = UUID.randomUUID();
        when(getUseCase.get(deviceId.toString())).thenReturn(view(deviceId, DeviceStatus.ACTIVE));

        mockMvc.perform(get("/api/v1/admin/physical/devices/{deviceId}", deviceId)
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(deviceId.toString())))
            .andExpect(content().string(not(org.hamcrest.Matchers.containsStringIgnoringCase("secret"))));
    }

    @Test
    void get_unknown_device_returns_404() throws Exception {
        when(getUseCase.get(anyString())).thenThrow(new DeviceNotFoundException());

        mockMvc.perform(get("/api/v1/admin/physical/devices/{deviceId}", UUID.randomUUID())
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("DEVICE_NOT_FOUND")));
    }

    @Test
    void get_malformed_device_id_returns_400() throws Exception {
        // GetPhysicalDeviceUseCase.get(String) parses the id internally via DeviceId.of(...)
        // (design C4) — the controller never parses it itself, so the mock is stubbed to
        // reproduce that real IllegalArgumentException.
        when(getUseCase.get("not-a-uuid")).thenThrow(new IllegalArgumentException("Invalid DeviceId format"));

        mockMvc.perform(get("/api/v1/admin/physical/devices/{deviceId}", "not-a-uuid")
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    @Test
    void rotate_returns_200_with_a_new_raw_secret() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        PhysicalDeviceSecretResult result =
            new PhysicalDeviceSecretResult(view(deviceId, DeviceStatus.ACTIVE), "new-raw-secret");
        when(rotateUseCase.rotate(deviceId.toString(), adminId)).thenReturn(result);

        mockMvc.perform(post("/api/v1/admin/physical/devices/{deviceId}/rotate-secret", deviceId)
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.secret", is("new-raw-secret")));

        verify(rotateUseCase).rotate(deviceId.toString(), adminId);
    }

    @Test
    void rotate_on_a_revoked_device_returns_409_with_the_revoked_code() throws Exception {
        when(rotateUseCase.rotate(anyString(), any())).thenThrow(new DeviceRevokedException());

        mockMvc.perform(post(
                "/api/v1/admin/physical/devices/{deviceId}/rotate-secret", UUID.randomUUID()
            ).principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("DEVICE_REVOKED")));
    }

    @Test
    void revoke_returns_200_with_the_revoked_status() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(revokeUseCase.revoke(deviceId.toString(), adminId)).thenReturn(view(deviceId, DeviceStatus.REVOKED));

        mockMvc.perform(post("/api/v1/admin/physical/devices/{deviceId}/revoke", deviceId)
                .principal(authOf(adminId, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("REVOKED")));

        verify(revokeUseCase).revoke(deviceId.toString(), adminId);
    }

    @Test
    void revoke_on_an_already_revoked_device_returns_409_with_the_already_revoked_code() throws Exception {
        when(revokeUseCase.revoke(anyString(), any())).thenThrow(new DeviceAlreadyRevokedException());

        mockMvc.perform(post(
                "/api/v1/admin/physical/devices/{deviceId}/revoke", UUID.randomUUID()
            ).principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code", is("DEVICE_ALREADY_REVOKED")));
    }

    @Test
    void revoke_malformed_device_id_returns_400() throws Exception {
        when(revokeUseCase.revoke(eq("not-a-uuid"), any()))
            .thenThrow(new IllegalArgumentException("Invalid DeviceId format"));

        mockMvc.perform(post(
                "/api/v1/admin/physical/devices/{deviceId}/revoke", "not-a-uuid"
            ).principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    @Test
    void list_returns_200_with_no_secret_field_for_any_device() throws Exception {
        when(listUseCase.list()).thenReturn(List.of(
            view(UUID.randomUUID(), DeviceStatus.ACTIVE), view(UUID.randomUUID(), DeviceStatus.REVOKED)
        ));

        mockMvc.perform(get("/api/v1/admin/physical/devices")
                .principal(authOf(UUID.randomUUID(), "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.devices.length()", is(2)))
            .andExpect(content().string(not(org.hamcrest.Matchers.containsStringIgnoringCase("secret"))))
            .andExpect(content().string(not(org.hamcrest.Matchers.containsStringIgnoringCase("Hash"))));
    }
}
