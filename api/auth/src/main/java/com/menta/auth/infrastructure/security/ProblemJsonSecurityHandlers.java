package com.menta.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Writes RFC 9457 responses for authentication and authorization failures. */
final class ProblemJsonSecurityHandlers {

    private ProblemJsonSecurityHandlers() {
    }

    static AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) -> write(
            response,
            objectMapper,
            HttpStatus.UNAUTHORIZED,
            "Authentication is required.",
            "AUTHENTICATION_REQUIRED"
        );
    }

    static AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) -> write(
            response,
            objectMapper,
            HttpStatus.FORBIDDEN,
            "You are not authorized to access this resource.",
            "ACCESS_DENIED"
        );
    }

    private static void write(
        HttpServletResponse response,
        ObjectMapper objectMapper,
        HttpStatus status,
        String detail,
        String code
    ) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://menta.dance/problems/" + code.toLowerCase(java.util.Locale.ROOT)));
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
