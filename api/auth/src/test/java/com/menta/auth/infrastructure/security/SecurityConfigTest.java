package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

class SecurityConfigTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void constructs_the_configuration_instance() {
        assertThat(new SecurityConfig()).isNotNull();
    }

    @Test
    void wires_the_role_authorization_manager_bean() {
        SecurityConfig config = new SecurityConfig();

        RoleAuthorizationManager manager = config.roleAuthorizationManager();

        assertThat(manager).isNotNull();
    }

    @Test
    void wires_the_user_details_service_bean() {
        SecurityConfig config = new SecurityConfig();

        UserDetailsService service = config.userDetailsService();

        assertThat(service).isInstanceOf(InMemoryUserDetailsManager.class);
    }

    @Test
    void builds_the_security_filter_chain_bean_via_a_minimal_spring_security_context() {
        context = new AnnotationConfigApplicationContext(SecurityConfig.class, TestSupportConfig.class);

        SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);

        assertThat(chain).isNotNull();
        assertThat(chain.getFilters()).isNotEmpty();
    }

    @Configuration
    static class TestSupportConfig {

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return mock(JwtAuthenticationFilter.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
