package com.planner.backend.config;

import com.planner.backend.auth.AuthTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthTokenInterceptor authTokenInterceptor;
    private final String corsAllowedOrigins;

    public WebConfig(
            AuthTokenInterceptor authTokenInterceptor,
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String corsAllowedOrigins
    ) {
        this.authTokenInterceptor = authTokenInterceptor;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authTokenInterceptor)
                .excludePathPatterns(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = parseAllowedOrigins()
                .toArray(String[]::new);

        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    private List<String> parseAllowedOrigins() {
        return Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.replaceAll("/+$", ""))
                .distinct()
                .toList();
    }

    @Bean
    public CorsFilter corsFilter() {
        List<String> origins = parseAllowedOrigins();

        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("X-Auth-Token"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return new CorsFilter(source);
    }
}
