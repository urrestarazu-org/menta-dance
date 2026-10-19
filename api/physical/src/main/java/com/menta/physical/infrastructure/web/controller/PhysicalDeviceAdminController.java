package com.menta.physical.infrastructure.web.controller;

import com.menta.physical.application.dto.PhysicalDeviceSecretResult;
import com.menta.physical.application.dto.PhysicalDeviceView;
import com.menta.physical.application.dto.RegisterPhysicalDeviceCommand;
import com.menta.physical.application.port.in.GetPhysicalDeviceUseCase;
import com.menta.physical.application.port.in.ListPhysicalDevicesUseCase;
import com.menta.physical.application.port.in.RegisterPhysicalDeviceUseCase;
import com.menta.physical.application.port.in.RevokePhysicalDeviceUseCase;
import com.menta.physical.application.port.in.RotatePhysicalDeviceSecretUseCase;
import com.menta.physical.infrastructure.web.dto.PhysicalDeviceListResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalDeviceResponse;
import com.menta.physical.infrastructure.web.dto.PhysicalDeviceSecretResponse;
import com.menta.physical.infrastructure.web.dto.RegisterPhysicalDeviceRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the device registry (#44, US-PHYSICAL-007, design C6). {@code SecurityConfig}
 * grants only ADMIN access to this prefix (C7); this class never checks the role itself — there
 * is no per-resource ownership concept for a device, unlike {@code /courses/**}/{@code
 * /sessions/**}.
 */
@RestController
@RequestMapping("/api/v1/admin/physical/devices")
@PhysicalDeviceEndpoint
public class PhysicalDeviceAdminController {

    private final RegisterPhysicalDeviceUseCase registerPhysicalDeviceUseCase;
    private final GetPhysicalDeviceUseCase getPhysicalDeviceUseCase;
    private final RotatePhysicalDeviceSecretUseCase rotatePhysicalDeviceSecretUseCase;
    private final RevokePhysicalDeviceUseCase revokePhysicalDeviceUseCase;
    private final ListPhysicalDevicesUseCase listPhysicalDevicesUseCase;

    public PhysicalDeviceAdminController(
        RegisterPhysicalDeviceUseCase registerPhysicalDeviceUseCase,
        GetPhysicalDeviceUseCase getPhysicalDeviceUseCase,
        RotatePhysicalDeviceSecretUseCase rotatePhysicalDeviceSecretUseCase,
        RevokePhysicalDeviceUseCase revokePhysicalDeviceUseCase,
        ListPhysicalDevicesUseCase listPhysicalDevicesUseCase
    ) {
        this.registerPhysicalDeviceUseCase = registerPhysicalDeviceUseCase;
        this.getPhysicalDeviceUseCase = getPhysicalDeviceUseCase;
        this.rotatePhysicalDeviceSecretUseCase = rotatePhysicalDeviceSecretUseCase;
        this.revokePhysicalDeviceUseCase = revokePhysicalDeviceUseCase;
        this.listPhysicalDevicesUseCase = listPhysicalDevicesUseCase;
    }

    @PostMapping
    public ResponseEntity<PhysicalDeviceSecretResponse> register(
        @Valid @RequestBody RegisterPhysicalDeviceRequest request, Authentication authentication
    ) {
        RegisterPhysicalDeviceCommand command =
            new RegisterPhysicalDeviceCommand(request.name(), request.location(), request.expiresAt());
        PhysicalDeviceSecretResult result =
            registerPhysicalDeviceUseCase.register(command, actingUserId(authentication));
        return ResponseEntity.status(HttpStatus.CREATED).body(PhysicalDeviceSecretResponse.from(result));
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<PhysicalDeviceResponse> get(@PathVariable String deviceId) {
        PhysicalDeviceView view = getPhysicalDeviceUseCase.get(deviceId);
        return ResponseEntity.ok(PhysicalDeviceResponse.from(view));
    }

    @PostMapping("/{deviceId}/rotate-secret")
    public ResponseEntity<PhysicalDeviceSecretResponse> rotateSecret(
        @PathVariable String deviceId, Authentication authentication
    ) {
        PhysicalDeviceSecretResult result =
            rotatePhysicalDeviceSecretUseCase.rotate(deviceId, actingUserId(authentication));
        return ResponseEntity.ok(PhysicalDeviceSecretResponse.from(result));
    }

    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<PhysicalDeviceResponse> revoke(
        @PathVariable String deviceId, Authentication authentication
    ) {
        PhysicalDeviceView view = revokePhysicalDeviceUseCase.revoke(deviceId, actingUserId(authentication));
        return ResponseEntity.ok(PhysicalDeviceResponse.from(view));
    }

    @GetMapping
    public ResponseEntity<PhysicalDeviceListResponse> list() {
        List<PhysicalDeviceView> views = listPhysicalDevicesUseCase.list();
        return ResponseEntity.ok(PhysicalDeviceListResponse.from(views));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
