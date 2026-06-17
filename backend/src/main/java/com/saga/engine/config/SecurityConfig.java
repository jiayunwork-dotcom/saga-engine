package com.saga.engine.config;

import com.saga.engine.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/health").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/saga-definitions/**").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/saga-definitions/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/saga-definitions/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/saga-definitions/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/saga-instances/trigger").authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/saga-instances/**").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/saga-instances/*/retry").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/saga-instances/*/compensate").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/saga-instances/*/pause").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/saga-instances/*/resume").hasRole("ADMIN")
                    .requestMatchers("/api/dead-letter/**").hasRole("ADMIN")
                    .requestMatchers("/api/statistics/**").authenticated()
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
