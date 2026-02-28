package com.watchparty.config;

import com.watchparty.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Authentication endpoints (public)
                .requestMatchers("/api/auth/**").permitAll()
                // Rooms: GET and POST are public (guests can join/create), mutating ops require auth
                .requestMatchers(HttpMethod.GET, "/api/rooms/{code}").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/rooms").permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/rooms/{code}").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/rooms/{code}").authenticated()
                // Videos: search/suggest are public, other ops require auth
                .requestMatchers(HttpMethod.GET, "/api/videos/**").permitAll()
                // Health check
                .requestMatchers("/api/health").permitAll()
                // WebSocket endpoint (auth handled by STOMP interceptor)
                .requestMatchers("/ws/**").permitAll()
                // API docs (dev only in practice, but accessible)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Static assets
                .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()
                .requestMatchers("/*.js", "/*.css", "/*.woff2", "/*.woff", "/*.ttf").permitAll()
                .requestMatchers("/*.png", "/*.svg", "/*.jpg", "/*.ico", "/*.webp").permitAll()
                .requestMatchers("/assets/**", "/media/**").permitAll()
                // SPA routes (Angular client-side routing, forwarded to index.html by SpaForwardingController)
                .requestMatchers("/room/**", "/login", "/register", "/settings/**").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
