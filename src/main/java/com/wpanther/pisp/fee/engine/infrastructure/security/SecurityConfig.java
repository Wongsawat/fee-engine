package com.wpanther.pisp.fee.engine.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain calculationChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/fee-calculations", "/fee-calculations/**", "/actuator/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain adminChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/admin/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/admin/**")
                            .hasAuthority("SCOPE_fee-rules:read")
                        .requestMatchers(HttpMethod.POST, "/admin/**")
                            .hasAuthority("SCOPE_fee-rules:write")
                        .requestMatchers(HttpMethod.PUT, "/admin/**")
                            .hasAuthority("SCOPE_fee-rules:write")
                        .requestMatchers(HttpMethod.PATCH, "/admin/**")
                            .hasAuthority("SCOPE_fee-rules:write")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .build();
    }
}
