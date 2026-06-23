package com.planner.backend.project.thing;

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
@RequestMapping("/api/things")
@CrossOrigin
public class ThingController {
    private static final Logger log = LoggerFactory.getLogger(ThingController.class);
    private final ThingService thingService;

    public ThingController(ThingService thingService) {
        this.thingService = thingService;
    }

    private static String username(HttpServletRequest req) {
        Object v = req.getAttribute(AuthTokenInterceptor.ATTR_USERNAME);
        return v != null ? v.toString() : "";
    }

    @GetMapping
    public List<ThingRecord> list(HttpServletRequest req) throws IOException {
        return thingService.listThings(username(req));
    }

    @GetMapping("/folders")
    public List<String> listFolders(HttpServletRequest req) throws IOException {
        return thingService.listFolders(username(req));
    }

    @PostMapping("/folders")
    public List<String> createFolder(@RequestBody CreateThingFolderRequest req,
                                     HttpServletRequest request) throws IOException {
        return thingService.createFolder(req, username(request));
    }

    @GetMapping("/{id}")
    public ThingRecord get(@PathVariable String id, HttpServletRequest req) throws IOException {
        return thingService.getThing(id, username(req));
    }

    @PostMapping
    public ThingRecord create(@RequestBody CreateThingRequest req, HttpServletRequest request) throws IOException {
        return thingService.createThing(req, username(request));
    }

    @PutMapping("/{id}")
    public ThingRecord update(@PathVariable String id,
                              @RequestBody UpdateThingRequest req,
                              HttpServletRequest request) throws IOException {
        return thingService.updateThing(id, req, username(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, HttpServletRequest req) throws IOException {
        thingService.deleteThing(id, username(req));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em things", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend."));
    }
}
