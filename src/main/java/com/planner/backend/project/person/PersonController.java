package com.planner.backend.project.person;

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
public class PersonController {
    private static final Logger log = LoggerFactory.getLogger(PersonController.class);
    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    // ── People ────────────────────────────────────────────────────────────────

    @GetMapping("/people")
    public List<Person> listPeople() throws IOException {
        return personService.listPeople();
    }

    @PostMapping("/people")
    public Person createPerson(@RequestBody CreatePersonRequest request) throws IOException {
        return personService.createPerson(request);
    }

    @PutMapping("/people/{personId}")
    public Person updatePerson(@PathVariable String personId, @RequestBody CreatePersonRequest request) throws IOException {
        return personService.updatePerson(personId, request);
    }

    @DeleteMapping("/people/{personId}")
    public ResponseEntity<Void> deletePerson(@PathVariable String personId) throws IOException {
        personService.deletePerson(personId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/people/import-csv")
    public ImportPeopleCsvResponse importPeopleCsv(@RequestBody ImportPeopleCsvRequest request) throws IOException {
        return personService.importPeopleCsv(request);
    }

    // ── Absences ──────────────────────────────────────────────────────────────

    @GetMapping("/absences")
    public List<Absence> listAbsences() throws IOException {
        return personService.listAbsences();
    }

    @PostMapping("/absences")
    public Absence createAbsence(@RequestBody CreateAbsenceRequest request) throws IOException {
        return personService.createAbsence(request);
    }

    @PutMapping("/absences/{absenceId}")
    public Absence updateAbsence(@PathVariable String absenceId, @RequestBody CreateAbsenceRequest request) throws IOException {
        return personService.updateAbsence(absenceId, request);
    }

    @DeleteMapping("/absences/{absenceId}")
    public ResponseEntity<Void> deleteAbsence(@PathVariable String absenceId) throws IOException {
        personService.deleteAbsence(absenceId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("Erro inesperado em endpoint de pessoas", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Falha interna no backend ao processar a solicitacao."));
    }
}
