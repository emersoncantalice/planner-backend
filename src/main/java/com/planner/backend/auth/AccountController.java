package com.planner.backend.auth;

import static com.planner.backend.auth.AuthModels.*;

import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AuthService authService;

    public AccountController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public AccountResponse me(@RequestHeader("X-Auth-Token") String token) throws IOException {
        return authService.me(token);
    }

    @PatchMapping
    public AccountResponse updateAccount(
            @RequestHeader("X-Auth-Token") String token,
            @RequestBody UpdateAccountRequest request) throws IOException {
        return authService.updateAccount(token, request);
    }

    @PatchMapping("/password")
    public Map<String, String> changePassword(
            @RequestHeader("X-Auth-Token") String token,
            @RequestBody ChangePasswordRequest request) throws IOException {
        authService.changePassword(token, request);
        return Map.of("message", "Senha alterada com sucesso.");
    }

    @GetMapping("/profile-image")
    public ProfileImageResponse profileImage(@RequestHeader("X-Auth-Token") String token) throws IOException {
        return authService.getProfileImage(token);
    }

    @PutMapping("/profile-image")
    public ProfileImageResponse updateProfileImage(
            @RequestHeader("X-Auth-Token") String token,
            @RequestBody UpdateProfileImageRequest request) throws IOException {
        return authService.updateProfileImage(token, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
