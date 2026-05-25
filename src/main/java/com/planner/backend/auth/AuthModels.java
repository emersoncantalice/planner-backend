package com.planner.backend.auth;

import java.time.OffsetDateTime;

public final class AuthModels {
    private AuthModels() {}

    public record UserRecord(
            String username,
            String nome,
            String passwordHash,
            OffsetDateTime createdAt,
            String role,    // "USER" | "ADMIN"
            String status,  // "PENDING" | "APPROVED"
            String email) {}

    public record SessionRecord(
            String token,
            String username,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {}

    public record AuthRequest(String username, String password, String nome) {}

    public record AuthResponse(
            String token,
            String username,
            String nome,
            String role,
            String status,
            OffsetDateTime expiresAt) {}

    public record AccountResponse(
            String username,
            String nome,
            OffsetDateTime criadoEm,
            String role,
            String status,
            String email) {}

    public record ProfileImageResponse(String dataUrl) {}

    public record UpdateAccountRequest(String username, String nome) {}
    public record UpdateProfileImageRequest(String dataUrl) {}
    public record ChangePasswordRequest(String senhaAtual, String novaSenha) {}

    // ── Admin user management ─────────────────────────────────────────────
    public record AdminUserView(
            String username,
            String nome,
            String email,
            String role,
            String status,
            OffsetDateTime criadoEm) {}

    public record AdminUpdateUserRequest(
            String nome,
            String email,
            String role,
            String status) {}

    public record AdminCreateUserRequest(
            String username,
            String password,
            String nome,
            String email,
            String role,
            String status) {}

    public record AdminResetPasswordResponse(String novaSenha) {}
}
