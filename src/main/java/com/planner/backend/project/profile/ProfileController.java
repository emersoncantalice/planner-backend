package com.planner.backend.project.profile;

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
@RequestMapping("/api")
@CrossOrigin
public class ProfileController {
    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profiles")
    public List<Profile> listProfiles() throws IOException {
        return profileService.listProfiles();
    }

    @PostMapping("/profiles")
    public Profile createProfile(@RequestBody CreateProfileRequest request) throws IOException {
        return profileService.createProfile(request);
    }

    @PostMapping("/profiles/import-csv")
    public ImportCsvResponse importProfilesCsv(@RequestBody ImportProfilesCsvRequest request) throws IOException {
        return profileService.importProfilesCsv(request);
    }

    @PutMapping("/profiles/{profileId}")
    public Profile updateProfile(@PathVariable String profileId, @RequestBody CreateProfileRequest request) throws IOException {
        return profileService.updateProfile(profileId, request);
    }

    @DeleteMapping("/profiles/{profileId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String profileId) throws IOException {
        profileService.deleteProfile(profileId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de profiles", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
