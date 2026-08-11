package com.menta.bff.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Configuration for Spring Session with Redis backend.
 * <p>
 * Enables server-side session storage in Redis with secure cookie attributes:
 * - HttpOnly: prevents XSS attacks from accessing session cookie
 * - Secure: requires HTTPS in production
 * - SameSite=Lax: prevents CSRF attacks while allowing normal navigation
 * - 30-minute TTL: aligns with access token lifetime
 * </p>
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes
public class RedisSessionConfig {

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }
}
