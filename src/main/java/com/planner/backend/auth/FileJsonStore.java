package com.planner.backend.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FileJsonStore {
    private static final Logger log = LoggerFactory.getLogger(FileJsonStore.class);
    private static final Map<Path, Object> PATH_LOCKS = new ConcurrentHashMap<>();
    private static final int MOVE_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 40L;
    private final ObjectMapper objectMapper;

    public FileJsonStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    
    public ObjectMapper getObjectMapper() { return objectMapper; }

    public <T> List<T> readList(Path path, TypeReference<List<T>> typeReference) throws IOException {
        if (!Files.exists(path)) {
            return List.of();
        }
        if (Files.size(path) == 0L) {
            return List.of();
        }
        try {
            return objectMapper.readValue(path.toFile(), typeReference);
        } catch (IOException ex) {
            log.error("Falha ao ler arquivo de dados '{}'. Retornando lista vazia. Causa: {}", path.getFileName(), ex.getMessage());
            return List.of();
        }
    }


    public <T> T readObject(Path path, TypeReference<T> typeReference) throws IOException {
        if (!Files.exists(path)) return null;
        if (Files.size(path) == 0L) return null;
        try {
            return objectMapper.readValue(path.toFile(), typeReference);
        } catch (IOException ex) {
            log.error("Falha ao ler arquivo de dados '{}'. Retornando null. Causa: {}", path.getFileName(), ex.getMessage());
            return null;
        }
    }

    
    public <T> void writeObject(Path path, T value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path normalizedPath = path.toAbsolutePath().normalize();
        Object lock = PATH_LOCKS.computeIfAbsent(normalizedPath, ignored -> new Object());
        synchronized (lock) {
            Path temp = normalizedPath.resolveSibling(normalizedPath.getFileName() + ".tmp-" + UUID.randomUUID());
            try {
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
                Files.writeString(temp, json, StandardCharsets.UTF_8);
                moveTempFile(temp, normalizedPath);
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException cleanupEx) {
                    log.warn("Falha ao limpar arquivo temporario {}: {}", temp, cleanupEx.getMessage());
                }
            }
        }
    }

    public <T> void writeList(Path path, List<T> values) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        Object lock = PATH_LOCKS.computeIfAbsent(normalizedPath, ignored -> new Object());
        synchronized (lock) {
            Path temp = normalizedPath.resolveSibling(normalizedPath.getFileName() + ".tmp-" + UUID.randomUUID());
            try {
                String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(values);
                Files.writeString(temp, json, StandardCharsets.UTF_8);
                moveTempFile(temp, normalizedPath);
            } finally {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException cleanupEx) {
                    log.warn("Falha ao limpar arquivo temporario {}: {}", temp, cleanupEx.getMessage());
                }
            }
        }
    }

    private static void moveTempFile(Path temp, Path destination) throws IOException {
        try {
            Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return;
        } catch (AccessDeniedException | java.nio.file.AtomicMoveNotSupportedException ex) {
            log.debug("ATOMIC_MOVE indisponivel/bloqueado para {}: {}. Aplicando fallback com retry.",
                    destination, ex.getMessage());
        }

        IOException lastError = null;
        for (int attempt = 1; attempt <= MOVE_RETRIES; attempt++) {
            try {
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException ex) {
                lastError = ex;
                if (attempt == MOVE_RETRIES) {
                    break;
                }
                sleepQuietly(RETRY_BACKOFF_MS * attempt);
            }
        }

        throw lastError != null ? lastError : new IOException("Falha ao mover arquivo temporario para destino.");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedEx) {
            Thread.currentThread().interrupt();
        }
    }
}
