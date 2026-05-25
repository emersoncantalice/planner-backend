package com.planner.backend.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.planner.backend.application.port.ProjectStorePort;
import com.planner.backend.auth.FileJsonStore;
import com.planner.backend.project.ProjectModels.ProjectRecord;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FileProjectStoreAdapter implements ProjectStorePort {
    private final FileJsonStore jsonStore;
    private final Path projectsPath;

    public FileProjectStoreAdapter(FileJsonStore jsonStore, @Value("${planner.data-dir:data}") String dataDir) {
        this.jsonStore = jsonStore;
        this.projectsPath = Path.of(dataDir, "projects.json");
    }

    @Override
    public List<ProjectRecord> loadProjects() throws IOException {
        return jsonStore.readList(projectsPath, new TypeReference<>() {});
    }

    @Override
    public void saveProjects(List<ProjectRecord> projects) throws IOException {
        jsonStore.writeList(projectsPath, projects);
    }
}
