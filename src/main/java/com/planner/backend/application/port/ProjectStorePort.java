package com.planner.backend.application.port;

import com.planner.backend.project.ProjectModels.ProjectRecord;
import java.io.IOException;
import java.util.List;

public interface ProjectStorePort {
    List<ProjectRecord> loadProjects() throws IOException;
    void saveProjects(List<ProjectRecord> projects) throws IOException;
}
