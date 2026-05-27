package com.planner.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthTokenInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuthTokenInterceptor.class);
    public static final String ATTR_USERNAME = "authed-username";
    public static final String ATTR_ROLE     = "authed-role";

    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public AuthTokenInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        String normalizedPath = path == null ? "" : path.toLowerCase();
        if (normalizedPath.startsWith("/api/auth")
                || normalizedPath.startsWith("/actuator/health")
                || normalizedPath.contains("/v3/api-docs")
                || normalizedPath.contains("swagger-ui")
                || normalizedPath.endsWith("/swagger-ui.html")) {
            return true;
        }

        String token = request.getHeader("X-Auth-Token");
        Optional<AuthModels.UserRecord> userOpt = authService.validateTokenFull(token);
        if (userOpt.isEmpty()) {
            log.warn("Acesso nao autorizado: method={} path={} remote={}",
                    request.getMethod(), path, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Nao autorizado."));
            return false;
        }

        AuthModels.UserRecord user = userOpt.get();
        request.setAttribute(ATTR_USERNAME, user.username());
        request.setAttribute(ATTR_ROLE, user.role() != null ? user.role() : "USER");

        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            log.info("Requisicao autenticada: method={} path={} user={} role={}",
                    request.getMethod(), path, user.username(), user.role());
        }
        return true;
    }
}
