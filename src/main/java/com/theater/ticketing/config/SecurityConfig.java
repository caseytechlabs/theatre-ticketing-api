package com.theater.ticketing.config;

import com.theater.ticketing.security.JwtAuthenticationFilter;
import com.theater.ticketing.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                // Vouchers — /my must be declared BEFORE /** wildcard
                .requestMatchers(HttpMethod.GET, "/api/v1/vouchers/my").hasRole("CLIENT")
                .requestMatchers(HttpMethod.GET, "/api/v1/vouchers").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/vouchers/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/vouchers").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/vouchers/**").hasRole("ADMIN")
                // Bookings — /my must be declared BEFORE /** wildcard
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings/my").hasRole("CLIENT")
                .requestMatchers(HttpMethod.GET, "/api/v1/bookings").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/initiate").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/bookings/confirm").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/v1/bookings/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/bookings/**").authenticated()
                // Users — admin only
                .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                // Unauthenticated requests (missing/invalid/expired token) → 401 so the
                // frontend Axios interceptor redirects to login instead of showing a raw 403.
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"Authentication required\"}");
                })
                // Authenticated user lacking the required role → 403 with a readable body
                // (application-level 403s from UnauthorizedAccessException still flow through
                //  GlobalExceptionHandler and are unaffected by this handler).
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json");
                    res.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    res.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"Access denied\"}");
                })
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
