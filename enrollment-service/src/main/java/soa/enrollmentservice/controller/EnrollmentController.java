package soa.enrollmentservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import soa.enrollmentservice.dto.EnrollmentRequest;
import soa.enrollmentservice.dto.EnrollmentResponse;
import soa.enrollmentservice.service.EnrollmentService;

@RestController
@RequestMapping("/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    // CREATE (SAGA ORCHESTRATION)
    @PostMapping
    public EnrollmentResponse createEnrollment(@RequestBody EnrollmentRequest request) {
        return enrollmentService.createEnrollment(request);
    }

    // READ ALL
    @GetMapping
    public List<EnrollmentResponse> getAllEnrollments() {
        return enrollmentService.getAllEnrollments();
    }

    // READ BY ID
    @GetMapping("/{id}")
    public EnrollmentResponse getEnrollmentById(@PathVariable Long id) {
        return enrollmentService.getEnrollmentById(id);
    }

    // READ BY STUDENT ID
    @GetMapping("/by-student/{studentId}")
    public List<EnrollmentResponse> getEnrollmentsByStudent(@PathVariable String studentId) {
        return enrollmentService.getEnrollmentsByStudent(studentId);
    }

    // SAGA RECONCILIATION ENDPOINT
    @PostMapping("/{id}/reconcile")
    public EnrollmentResponse reconcileEnrollment(@PathVariable Long id) {
        return enrollmentService.reconcileEnrollment(id);
    }

    // SAGA CANCELLATION & COMPENSATION ENDPOINT
    @PostMapping("/{id}/cancel")
    public EnrollmentResponse cancelEnrollment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Cancelled by user or administrator") String reason) {
        return enrollmentService.cancelEnrollment(id, reason);
    }

    // EXISTS CHECK (used by Payment Service via Feign)
    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> existsById(@PathVariable Long id) {
        try {
            enrollmentService.getEnrollmentById(id);
            return ResponseEntity.ok(true);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
        }
    }

    // UPDATE
    @PutMapping("/{id}")
    public EnrollmentResponse updateEnrollment(
            @PathVariable Long id,
            @RequestBody EnrollmentRequest request) {
        return enrollmentService.updateEnrollment(id, request);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public String deleteEnrollment(@PathVariable Long id) {
        enrollmentService.deleteEnrollment(id);
        return "Enrollment deleted successfully";
    }
}