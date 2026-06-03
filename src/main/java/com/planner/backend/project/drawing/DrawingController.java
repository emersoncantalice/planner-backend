package com.planner.backend.project.drawing;

import static com.planner.backend.project.ProjectModels.*;

import com.planner.backend.auth.AuthTokenInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/drawings")
@CrossOrigin
public class DrawingController {
    private static final Logger log = LoggerFactory.getLogger(DrawingController.class);
    private final DrawingService drawingService;

    public DrawingController(DrawingService drawingService) {
        this.drawingService = drawingService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    @GetMapping
    public List<DrawingRecord> list(HttpServletRequest req) throws IOException {
        return drawingService.listDrawings(username(req));
    }

    @GetMapping("/{id}")
    public DrawingRecord get(@PathVariable String id, HttpServletRequest req) throws IOException {
        return drawingService.getDrawing(id, username(req));
    }

    @PostMapping
    public DrawingRecord create(@RequestBody CreateDrawingRequest req, HttpServletRequest request) throws IOException {
        return drawingService.createDrawing(req, username(request));
    }

    @PutMapping("/{id}")
    public DrawingRecord update(@PathVariable String id,
                                 @RequestBody UpdateDrawingRequest req,
                                 HttpServletRequest request) throws IOException {
        return drawingService.updateDrawing(id, req, username(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest req) throws IOException {
        drawingService.deleteDrawing(id, username(req));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em drawings", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend."));
    }
}
