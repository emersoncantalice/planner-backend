package com.planner.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.planner.backend.application.port.AuthStorePort;
import com.planner.backend.auth.AuthModels.SessionRecord;
import com.planner.backend.auth.AuthModels.UserRecord;
import com.planner.backend.auth.FileJsonStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileAuthStoreAdapter implements AuthStorePort {
    private final FileJsonStore jsonStore;
    private final Path usersPath;
    private final Path sessionsPath;

    public FileAuthStoreAdapter(FileJsonStore jsonStore, @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.usersPath = Path.of(dataDir, "users.json");
        this.sessionsPath = Path.of(dataDir, "sessions.json");
    }

    @Override
    public List<UserRecord> loadUsers() throws IOException {
        return jsonStore.readList(usersPath, new TypeReference<>() {});
    }

    @Override
    public void saveUsers(List<UserRecord> users) throws IOException {
        jsonStore.writeList(usersPath, users);
    }

    @Override
    public List<SessionRecord> loadSessions() throws IOException {
        return jsonStore.readList(sessionsPath, new TypeReference<>() {});
    }

    @Override
    public void saveSessions(List<SessionRecord> sessions) throws IOException {
        jsonStore.writeList(sessionsPath, sessions);
    }
}
