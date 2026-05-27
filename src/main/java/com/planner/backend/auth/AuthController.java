package com.planner.backend.auth;

import static com.planner.backend.auth.AuthModels.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody AuthRequest request) throws IOException {
        log.info("POST /api/auth/register username={}", request != null ? request.username() : null);
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody AuthRequest request) throws IOException {
        log.info("POST /api/auth/login username={}", request != null ? request.username() : null);
        return authService.login(request);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(
            @RequestHeader(value = "X-Auth-Token", required = false) String token) throws IOException {
        log.info("POST /api/auth/logout tokenPresent={}", token != null && !token.isBlank());
        authService.logout(token);
        return Map.of("message", "Sessao encerrada.");
    }

    @GetMapping("/me")
    public AccountResponse me(
            @RequestHeader(value = "X-Auth-Token", required = false) String token) throws IOException {
        log.info("GET /api/auth/me tokenPresent={}", token != null && !token.isBlank());
        return authService.me(token);
    }

    @GetMapping("/users")
    public List<AdminUserView> listUsers(
            @RequestHeader(value = "X-Auth-Token", required = false) String token) throws IOException {
        return authService.adminListUsers(token);
    }

    @PostMapping("/users")
    public AdminUserView createUser(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestBody AdminCreateUserRequest request) throws IOException {
        return authService.adminCreateUser(token, request);
    }

    @PutMapping("/users/{username}")
    public AdminUserView updateUser(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String username,
            @RequestBody AdminUpdateUserRequest request) throws IOException {
        return authService.adminUpdateUser(token, username, request);
    }

    @PostMapping("/users/{username}/reset-password")
    public AdminResetPasswordResponse resetPassword(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String username) throws IOException {
        return authService.adminResetPassword(token, username);
    }

    @DeleteMapping("/users/{username}")
    public ResponseEntity<Void> deleteUser(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String username) throws IOException {
        authService.adminDeleteUser(token, username);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(AuthService.PendingApprovalException.class)
    public ResponseEntity<Map<String, String>> handlePending(AuthService.PendingApprovalException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage(), "code", "PENDING_APPROVAL"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de autenticacao", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no servidor de autenticacao."));
    }
}
