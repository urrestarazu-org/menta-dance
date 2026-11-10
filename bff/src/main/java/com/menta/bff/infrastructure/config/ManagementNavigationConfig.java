package com.menta.bff.infrastructure.config;

import com.menta.bff.application.management.ManagementNavigation;
import com.menta.bff.application.management.ManagementSection;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Catalog of the management area's sections (#354).
 *
 * <p>The bean is named {@code managementNavigation} because the shared management layout reads it from
 * the template ({@code @managementNavigation}). The roles of each section mirror the prefix rules in
 * {@link BffSecurityConfig}.
 * </p>
 */
@Configuration
public class ManagementNavigationConfig {

    /**
     * Every planned section is declared but disabled: none has a screen yet, and an enabled entry
     * without one would be a dead link. Each screen issue (#367, #368, #370, #372, #373, ...) turns its
     * own section on ({@code enabled = true}) in the same change that adds its controller.
     */
    @Bean
    public ManagementNavigation managementNavigation() {
        return new ManagementNavigation(List.of(
                new ManagementSection("content", "Contenido", "/gestion/contenido",
                        Set.of("ADMIN", "INSTRUCTOR"), false),
                new ManagementSection("sessions", "Sesiones", "/gestion/sesiones",
                        Set.of("ADMIN", "INSTRUCTOR"), false),
                new ManagementSection("reception", "Recepción", "/gestion/recepcion",
                        Set.of("RECEPTIONIST", "ADMIN"), false),
                new ManagementSection("billing", "Facturación", "/gestion/billing",
                        Set.of("ADMIN"), false),
                new ManagementSection("devices", "Dispositivos", "/gestion/dispositivos",
                        Set.of("ADMIN"), false)));
    }
}
