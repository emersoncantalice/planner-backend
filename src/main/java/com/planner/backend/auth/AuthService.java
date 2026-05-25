package com.planner.backend.auth;

import static com.planner.backend.auth.AuthModels.*;

import com.planner.backend.application.port.AuthStorePort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_PROFILE_IMAGE_CHARS = 5_000_000;
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER  = "USER";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_PENDING  = "PENDING";

    private final AuthStorePort authStorePort;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Path profileImagesDir;

    public AuthService(AuthStorePort authStorePort, @Value("${planner.data-dir:data}") String dataDir) {
        this.authStorePort = authStorePort;
        this.profileImagesDir = resolveProfileImagesDir(dataDir);
    }

    // ── Register ──────────────────────────────────────────────────────────
    public AuthResponse register(AuthRequest request) throws IOException {
        validateRequest(request);
        log.info("Tentativa de cadastro para usuario={}", request.username());
        List<UserRecord> users = new ArrayList<>(loadUsersSafe());
        boolean exists = users.stream().anyMatch(u -> usernamesMatch(u.username(), request.username()));
        if (exists) {
            throw new IllegalArgumentException("Usuario ja existe.");
        }

        String nome = request.nome() == null || request.nome().isBlank()
                ? request.username().trim() : request.nome().trim();

        // First user ever → ADMIN + APPROVED; everyone else → USER + PENDING
        boolean isFirstUser = users.isEmpty();
        String role   = isFirstUser ? ROLE_ADMIN : ROLE_USER;
        String status = isFirstUser ? STATUS_APPROVED : STATUS_PENDING;

        UserRecord newUser = new UserRecord(
                request.username().trim(), nome,
                passwordEncoder.encode(request.password()),
                OffsetDateTime.now(), role, status, null);
        users.add(newUser);
        saveUsers(users);
        log.info("Cadastro concluido para usuario={} role={} status={}", request.username(), role, status);

        if (STATUS_PENDING.equals(status)) {
            // Return a special response with no token; frontend detects status=PENDING
            return new AuthResponse(null, newUser.username(), newUser.nome(), role, STATUS_PENDING, null);
        }
        // Admin self-register: log in immediately
        return login(request);
    }

    // ── Login ─────────────────────────────────────────────────────────────
    public AuthResponse login(AuthRequest request) throws IOException {
        validateRequest(request);
        log.info("Tentativa de login para usuario={}", request.username());
        Optional<UserRecord> userOpt = loadUsersSafe().stream()
                .filter(u -> usernamesMatch(u.username(), request.username()))
                .findFirst();
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.password(), userOpt.get().passwordHash())) {
            log.warn("Falha de login para usuario={}", request.username());
            throw new IllegalArgumentException("Login ou senha invalidos.");
        }
        UserRecord user = userOpt.get();
        String status = safeStatus(user);
        if (STATUS_PENDING.equals(status)) {
            log.warn("Login bloqueado - usuario pendente de aprovacao: {}", request.username());
            throw new PendingApprovalException("Seu cadastro aguarda aprovação do administrador.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        SessionRecord session = new SessionRecord(UUID.randomUUID().toString(), user.username(), now, null);
        List<SessionRecord> sessions = new ArrayList<>(loadSessions());
        sessions.add(session);
        saveSessions(sessions);
        log.info("Login concluido para usuario={}", session.username());
        return new AuthResponse(session.token(), session.username(), safeNome(user), safeRole(user), status, session.expiresAt());
    }

    // ── Token validation ──────────────────────────────────────────────────
    public Optional<String> validateToken(String token) throws IOException {
        if (token == null || token.isBlank()) return Optional.empty();
        List<SessionRecord> sessions = loadSessions();
        return sessions.stream()
                .filter(s -> s.token().equals(token))
                .map(SessionRecord::username)
                .findFirst();
    }

    /** Returns the UserRecord for a valid token, or empty if invalid. */
    public Optional<UserRecord> validateTokenFull(String token) throws IOException {
        Optional<String> usernameOpt = validateToken(token);
        if (usernameOpt.isEmpty()) return Optional.empty();
        String username = usernameOpt.get();
        return loadUsersSafe().stream()
                .filter(u -> usernamesMatch(u.username(), username))
                .findFirst();
    }

    // ── Logout ────────────────────────────────────────────────────────────
    public void logout(String token) throws IOException {
        if (token == null || token.isBlank()) return;
        List<SessionRecord> sessions = new ArrayList<>(loadSessions());
        boolean removed = sessions.removeIf(s -> s.token().equals(token));
        if (removed) saveSessions(sessions);
    }

    // ── Me ────────────────────────────────────────────────────────────────
    public AccountResponse me(String token) throws IOException {
        String username = validateToken(token).orElseThrow(() -> new IllegalArgumentException("Não autorizado."));
        UserRecord user = loadUsersSafe().stream()
                .filter(u -> usernamesMatch(u.username(), username))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado."));
        return toAccountResponse(user);
    }

    // ── Update account (self) ─────────────────────────────────────────────
    public AccountResponse updateAccount(String token, UpdateAccountRequest request) throws IOException {
        if (request == null || request.username() == null || request.username().isBlank())
            throw new IllegalArgumentException("Username e obrigatorio.");
        if (request.nome() == null || request.nome().isBlank())
            throw new IllegalArgumentException("Nome e obrigatorio.");

        String currentUsername = validateToken(token).orElseThrow(() -> new IllegalArgumentException("Não autorizado."));
        String newUsername = request.username().trim();
        String newNome = request.nome().trim();

        List<UserRecord> users = new ArrayList<>(loadUsersSafe());
        int idx = -1;
        for (int i = 0; i < users.size(); i++) {
            if (usernamesMatch(users.get(i).username(), currentUsername)) { idx = i; break; }
        }
        if (idx < 0) throw new IllegalArgumentException("Usuário não encontrado.");

        boolean usernameInUse = users.stream()
                .anyMatch(u -> !usernamesMatch(u.username(), currentUsername) && usernamesMatch(u.username(), newUsername));
        if (usernameInUse) throw new IllegalArgumentException("Ja existe usuario com esse username.");

        UserRecord cur = users.get(idx);
        UserRecord updated = new UserRecord(newUsername, newNome, cur.passwordHash(), cur.createdAt(),
                safeRole(cur), safeStatus(cur), cur.email());
        users.set(idx, updated);
        saveUsers(users);
        if (!usernamesMatch(currentUsername, newUsername)) moveProfileImage(currentUsername, newUsername);

        List<SessionRecord> sessions = new ArrayList<>(loadSessions());
        boolean changed = false;
        for (int i = 0; i < sessions.size(); i++) {
            SessionRecord s = sessions.get(i);
            if (token != null && token.equals(s.token())) {
                sessions.set(i, new SessionRecord(s.token(), newUsername, s.createdAt(), s.expiresAt()));
                changed = true;
            }
        }
        if (changed) saveSessions(sessions);
        return toAccountResponse(updated);
    }

    // ── Profile image ─────────────────────────────────────────────────────
    public ProfileImageResponse getProfileImage(String token) throws IOException {
        String username = validateToken(token).orElseThrow(() -> new IllegalArgumentException("Não autorizado."));
        String dataUrl = readProfileImage(username);
        return new ProfileImageResponse(dataUrl == null ? "" : dataUrl);
    }

    public ProfileImageResponse updateProfileImage(String token, UpdateProfileImageRequest request) throws IOException {
        String username = validateToken(token).orElseThrow(() -> new IllegalArgumentException("Não autorizado."));
        String dataUrl = request == null || request.dataUrl() == null ? "" : request.dataUrl().trim();
        if (!dataUrl.isBlank()) {
            if (!dataUrl.startsWith("data:image/")) throw new IllegalArgumentException("Formato de imagem invalido.");
            if (dataUrl.length() > MAX_PROFILE_IMAGE_CHARS) throw new IllegalArgumentException("Imagem muito grande.");
            writeProfileImage(username, dataUrl);
            return new ProfileImageResponse(dataUrl);
        }
        deleteProfileImage(username);
        return new ProfileImageResponse("");
    }

    // ── Change password (self) ────────────────────────────────────────────
    public void changePassword(String token, ChangePasswordRequest request) throws IOException {
        if (request == null || request.senhaAtual() == null || request.novaSenha() == null)
            throw new IllegalArgumentException("Requisicao invalida.");
        if (request.novaSenha().isBlank() || request.senhaAtual().isBlank())
            throw new IllegalArgumentException("Senha atual e nova senha sao obrigatorias.");

        String username = validateToken(token).orElseThrow(() -> new IllegalArgumentException("Não autorizado."));
        log.info("Solicitacao de troca de senha para usuario={}", username);
        List<UserRecord> users = new ArrayList<>(loadUsersSafe());
        for (int i = 0; i < users.size(); i++) {
            UserRecord u = users.get(i);
            if (usernamesMatch(u.username(), username)) {
                if (!passwordEncoder.matches(request.senhaAtual(), u.passwordHash()))
                    throw new IllegalArgumentException("Senha atual invalida.");
                users.set(i, new UserRecord(u.username(), safeNome(u),
                        passwordEncoder.encode(request.novaSenha()),
                        u.createdAt(), safeRole(u), safeStatus(u), u.email()));
                saveUsers(users);
                log.info("Senha alterada com sucesso para usuario={}", username);
                return;
            }
        }
        throw new IllegalArgumentException("Usuário não encontrado.");
    }

    // ── Admin: list users ─────────────────────────────────────────────────
    public List<AdminUserView> adminListUsers(String token) throws IOException {
        requireAdmin(token);
        return loadUsersSafe().stream().map(this::toAdminView).toList();
    }


    // ── Admin: create user ─────────────────────────────────────────────────
    public AdminUserView adminCreateUser(String token, AdminCreateUserRequest req) throws IOException {
        requireAdmin(token);
        if (req == null) throw new IllegalArgumentException("Requisicao invalida.");
        if (req.username() == null || req.username().isBlank())
            throw new IllegalArgumentException("Username e obrigatorio.");
        if (req.password() == null || req.password().isBlank())
            throw new IllegalArgumentException("Senha e obrigatoria.");

        String username = req.username().trim();
        List<UserRecord> users = new ArrayList<>(loadUsersSafe());
        boolean exists = users.stream().anyMatch(u -> usernamesMatch(u.username(), username));
        if (exists) throw new IllegalArgumentException("Usuario ja existe.");

        String nome = (req.nome() == null || req.nome().isBlank()) ? username : req.nome().trim();
        String role = (req.role() == null || req.role().isBlank()) ? ROLE_USER : req.role().trim().toUpperCase();
        String status = (req.status() == null || req.status().isBlank()) ? STATUS_APPROVED : req.status().trim().toUpperCase();
        String emailRaw = req.email() != null ? req.email().trim() : null;
        String email = (emailRaw == null || emailRaw.isBlank()) ? null : emailRaw;

        if (!ROLE_ADMIN.equals(role) && !ROLE_USER.equals(role))
            throw new IllegalArgumentException("Role invalido. Use USER ou ADMIN.");
        if (!STATUS_APPROVED.equals(status) && !STATUS_PENDING.equals(status))
            throw new IllegalArgumentException("Status invalido. Use PENDING ou APPROVED.");

        UserRecord created = new UserRecord(
                username,
                nome,
                passwordEncoder.encode(req.password()),
                OffsetDateTime.now(),
                role,
                status,
                email);
        users.add(created);
        saveUsers(users);
        log.info("Admin criou usuario={} role={} status={}", username, role, status);
        return toAdminView(created);
    }    // ── Admin: update user ────────────────────────────────────────────────
    public AdminUserView adminUpdateUser(String token, String targetUsername, AdminUpdateUserRequest req) throws IOException {
        requireAdmin(token);
        if (req == null) throw new IllegalArgumentException("Requisição inválida.");

        List<UserRecord> users = new ArrayList<>(loadUsersSafe());
        int idx = -1;
        for (int i = 0; i < users.size(); i++) {
            if (usernamesMatch(users.get(i).username(), targetUsername)) { idx = i; break; }
        }
        if (idx < 0) throw new IllegalArgumentException("Usuário não encontrado.");

        UserRecord cur = users.get(idx);
        String newNome   = (req.nome()   != null && !req.nome().isBlank())   ? req.nome().trim()   : safeNome(cur);
        String newRole   = (req.role()   != null && !req.role().isBlank())   ? req.role().trim().toUpperCase()   : safeRole(cur);
        String newStatus = (req.status() != null && !req.status().isBlank()) ? req.status().trim().toUpperCase() : safeStatus(cur);
        String newEmailRaw = req.email() != null ? req.email().trim() : cur.email();
        String newEmail = (newEmailRaw == null || newEmailRaw.isBlank()) ? null : newEmailRaw;

        if (!ROLE_ADMIN.equals(newRole) && !ROLE_USER.equals(newRole))
            throw new IllegalArgumentException("Role inválido. Use USER ou ADMIN.");
        if (!STATUS_APPROVED.equals(newStatus) && !STATUS_PENDING.equals(newStatus))
            throw new IllegalArgumentException("Status inválido. Use PENDING ou APPROVED.");

        UserRecord updated = new UserRecord(cur.username(), newNome, cur.passwordHash(),
                cur.createdAt(), newRole, newStatus, newEmail);
        users.set(idx, updated);
        saveUsers(users);
        log.info("Admin atualizou usuario={} role={} status={}", targetUsername, newRole, newStatus);
        return toAdminView(updated);
    }

    // ── Admin: reset password ─────────────────────────────────────────────
    public AdminResetPasswordResponse adminResetPassword(String token, String targetUsername) throws IOException {
        requireAdmin(token);
        List<UserRecord> users = new ArrayList<>(loadUsersSafe());
        int idx = -1;
        for (int i = 0; i < users.size(); i++) {
            if (usernamesMatch(users.get(i).username(), targetUsername)) { idx = i; break; }
        }
        if (idx < 0) throw new IllegalArgumentException("Usuário não encontrado.");

        // Generate a random 12-char password
        String newPass = generatePassword();
        UserRecord cur = users.get(idx);
        users.set(idx, new UserRecord(cur.username(), safeNome(cur),
                passwordEncoder.encode(newPass),
                cur.createdAt(), safeRole(cur), safeStatus(cur), cur.email()));
        saveUsers(users);
        log.info("Admin resetou senha do usuario={}", targetUsername);
        // Invalidate all sessions for this user
        List<SessionRecord> sessions = new ArrayList<>(loadSessions());
        sessions.removeIf(s -> usernamesMatch(s.username(), targetUsername));
        saveSessions(sessions);
        return new AdminResetPasswordResponse(newPass);
    }

    // ── Admin: delete user ────────────────────────────────────────────────
    public void adminDeleteUser(String token, String targetUsername) throws IOException {
        String adminUsername = requireAdmin(token);
        if (usernamesMatch(adminUsername, targetUsername))
            throw new IllegalArgumentException("Você não pode excluir sua própria conta.");
        List<UserRecord> users = new ArrayList<>(loadUsersSafe());
        boolean removed = users.removeIf(u -> usernamesMatch(u.username(), targetUsername));
        if (!removed) throw new IllegalArgumentException("Usuário não encontrado.");
        saveUsers(users);
        // Invalidate sessions
        List<SessionRecord> sessions = new ArrayList<>(loadSessions());
        sessions.removeIf(s -> usernamesMatch(s.username(), targetUsername));
        saveSessions(sessions);
        log.info("Admin excluiu usuario={}", targetUsername);
    }

    // ── Role helper (public, used by interceptor) ─────────────────────────
    public String getUserRole(String username) throws IOException {
        return loadUsersSafe().stream()
                .filter(u -> usernamesMatch(u.username(), username))
                .map(this::safeRole)
                .findFirst()
                .orElse(ROLE_USER);
    }

    // ── Private helpers ───────────────────────────────────────────────────
    private String requireAdmin(String token) throws IOException {
        String username = validateToken(token).orElseThrow(() -> new IllegalArgumentException("Não autorizado."));
        String role = getUserRole(username);
        if (!ROLE_ADMIN.equals(role)) throw new IllegalArgumentException("Acesso negado. Requer perfil ADMIN.");
        return username;
    }

    private List<UserRecord> loadUsers() throws IOException {
        return authStorePort.loadUsers();
    }

    private List<UserRecord> loadUsersSafe() throws IOException {
        List<UserRecord> all = loadUsers();
        // Normalise legacy records (role/status null)
        List<UserRecord> valid = new ArrayList<>();
        boolean firstUser = true;
        for (UserRecord u : all) {
            if (u == null || u.username() == null || u.username().isBlank()
                    || u.passwordHash() == null || u.passwordHash().isBlank()) {
                log.warn("Registro de usuario invalido ignorado.");
                continue;
            }
            // Migration: backfill role/status for records created before this feature
            String role   = u.role()   != null ? u.role()   : (firstUser ? ROLE_ADMIN : ROLE_USER);
            String status = u.status() != null ? u.status() : STATUS_APPROVED;
            valid.add(new UserRecord(u.username(), u.nome(), u.passwordHash(), u.createdAt(), role, status, u.email()));
            firstUser = false;
        }
        return valid;
    }

    private List<SessionRecord> loadSessions() throws IOException { return authStorePort.loadSessions(); }
    private void saveUsers(List<UserRecord> users) throws IOException { authStorePort.saveUsers(users); }
    private void saveSessions(List<SessionRecord> sessions) throws IOException { authStorePort.saveSessions(sessions); }

    private static void validateRequest(AuthRequest request) {
        if (request == null || request.username() == null || request.password() == null)
            throw new IllegalArgumentException("Requisicao invalida.");
        if (request.username().isBlank() || request.password().isBlank())
            throw new IllegalArgumentException("Usuario e senha sao obrigatorios.");
    }

    private static boolean usernamesMatch(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static String safeNome(UserRecord u) {
        if (u == null) return "";
        return u.nome() == null || u.nome().isBlank() ? u.username() : u.nome();
    }

    private String safeRole(UserRecord u)   { return u.role()   != null ? u.role()   : ROLE_USER; }
    private String safeStatus(UserRecord u) { return u.status() != null ? u.status() : STATUS_APPROVED; }

    private AccountResponse toAccountResponse(UserRecord u) {
        return new AccountResponse(u.username(), safeNome(u), u.createdAt(),
                safeRole(u), safeStatus(u), u.email());
    }

    private AdminUserView toAdminView(UserRecord u) {
        return new AdminUserView(u.username(), safeNome(u), u.email(),
                safeRole(u), safeStatus(u), u.createdAt());
    }

    private static String generatePassword() {
        String chars = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#$";
        StringBuilder sb = new StringBuilder(12);
        java.util.Random rng = new java.security.SecureRandom();
        for (int i = 0; i < 12; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    // ── Profile image helpers ─────────────────────────────────────────────
    private Path profileImagePath(String username) {
        String safe = username == null ? "" : username.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        if (safe.isBlank()) safe = "user";
        return profileImagesDir.resolve(safe + ".txt");
    }

    private String readProfileImage(String username) throws IOException {
        Path path = profileImagePath(username);
        if (!Files.exists(path)) return "";
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private void writeProfileImage(String username, String dataUrl) throws IOException {
        Files.createDirectories(profileImagesDir);
        Files.writeString(profileImagePath(username), dataUrl, StandardCharsets.UTF_8);
    }

    private void deleteProfileImage(String username) throws IOException {
        Files.deleteIfExists(profileImagePath(username));
    }

    private void moveProfileImage(String oldUsername, String newUsername) throws IOException {
        Path oldPath = profileImagePath(oldUsername);
        if (!Files.exists(oldPath)) return;
        Files.createDirectories(profileImagesDir);
        Files.move(oldPath, profileImagePath(newUsername), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private Path resolveProfileImagesDir(String dataDir) {
        String raw = dataDir == null ? "" : dataDir.trim();
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2)
            raw = raw.substring(1, raw.length() - 1).trim();
        if (raw.isBlank()) raw = "data";
        try { return Path.of(raw, "profile-images"); }
        catch (InvalidPathException ex) {
            log.warn("planner.data-dir invalido ({}). Usando fallback.", raw);
            return Path.of("data", "profile-images");
        }
    }

    // ── Exception type ────────────────────────────────────────────────────
    public static class PendingApprovalException extends RuntimeException {
        public PendingApprovalException(String msg) { super(msg); }
    }
}

