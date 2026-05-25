package com.planner.backend.application.port;

import com.planner.backend.auth.AuthModels.SessionRecord;
import com.planner.backend.auth.AuthModels.UserRecord;
import java.io.IOException;
import java.util.List;

public interface AuthStorePort {
    List<UserRecord> loadUsers() throws IOException;
    void saveUsers(List<UserRecord> users) throws IOException;
    List<SessionRecord> loadSessions() throws IOException;
    void saveSessions(List<SessionRecord> sessions) throws IOException;
}
