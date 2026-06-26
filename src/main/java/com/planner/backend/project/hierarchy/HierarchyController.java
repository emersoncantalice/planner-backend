package com.planner.backend.project.hierarchy;

import static com.planner.backend.project.ProjectModels.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hierarchy")
@CrossOrigin
public class HierarchyController {
    private static final Logger log = LoggerFactory.getLogger(HierarchyController.class);
    private final HierarchyService hierarchyService;

    public HierarchyController(HierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    @GetMapping
    public List<HierarchyNode> list(@RequestParam(required = false) String viewId) throws IOException {
        return hierarchyService.listNodes(viewId);
    }

    // ── Visoes/cenarios (abas) ────────────────────────────────────────────────

    @GetMapping("/views")
    public List<HierarchyView> listViews() throws IOException {
        return hierarchyService.listViews();
    }

    @PostMapping("/views")
    public HierarchyView createView(@RequestBody HierarchyViewRequest request) throws IOException {
        return hierarchyService.createView(request == null ? null : request.nome());
    }

    @PostMapping("/views/{viewId}/duplicate")
    public HierarchyView duplicateView(@PathVariable String viewId, @RequestBody HierarchyViewRequest request) throws IOException {
        return hierarchyService.duplicateView(viewId, request == null ? null : request.nome());
    }

    @PutMapping("/views/{viewId}")
    public HierarchyView renameView(@PathVariable String viewId, @RequestBody HierarchyViewRequest request) throws IOException {
        return hierarchyService.renameView(viewId, request == null ? null : request.nome());
    }

    @DeleteMapping("/views/{viewId}")
    public ResponseEntity<Void> deleteView(@PathVariable String viewId) throws IOException {
        hierarchyService.deleteView(viewId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public HierarchyNode create(@RequestBody CreateHierarchyNodeRequest request) throws IOException {
        return hierarchyService.createNode(request);
    }

    @PutMapping("/{nodeId}")
    public HierarchyNode update(@PathVariable String nodeId, @RequestBody CreateHierarchyNodeRequest request) throws IOException {
        return hierarchyService.updateNode(nodeId, request);
    }

    @PutMapping("/move-member")
    public List<HierarchyNode> moveMember(@RequestBody MoveHierarchyMemberRequest request) throws IOException {
        return hierarchyService.moveMember(request);
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> delete(@PathVariable String nodeId) throws IOException {
        hierarchyService.deleteNode(nodeId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de hierarquia", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
