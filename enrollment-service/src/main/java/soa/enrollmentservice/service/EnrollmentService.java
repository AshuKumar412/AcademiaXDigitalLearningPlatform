package soa.enrollmentservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import soa.enrollmentservice.client.CourseClient;
import soa.enrollmentservice.client.PaymentClient;
import soa.enrollmentservice.client.UserClient;
import soa.enrollmentservice.dto.CourseClientResponse;
import soa.enrollmentservice.dto.EnrollmentRequest;
import soa.enrollmentservice.dto.EnrollmentResponse;
import soa.enrollmentservice.dto.PaymentClientRequest;
import soa.enrollmentservice.dto.PaymentClientResponse;
import soa.enrollmentservice.exception.EnrollmentNotFoundException;
import soa.enrollmentservice.model.Enrollment;
import soa.enrollmentservice.repository.EnrollmentRepository;

@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private final EnrollmentRepository enrollmentRepository;
    private final CourseClient courseClient;
    private final PaymentClient paymentClient;
    private final UserClient userClient;

    @Value("${tuition.default-amount:500.00}")
    private BigDecimal defaultTuitionAmount;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             CourseClient courseClient,
                             PaymentClient paymentClient,
                             UserClient userClient) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseClient = courseClient;
        this.paymentClient = paymentClient;
        this.userClient = userClient;
    }

    // ==========================================
    // SAGA ORCHESTRATION: CREATE ENROLLMENT
    // ==========================================
    public EnrollmentResponse createEnrollment(EnrollmentRequest request) {

        String studentId = request.getStudentId();

        // Enforce authenticated student identity
        if (isCurrentUserStudent()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && !currentUser.isBlank()) {
                if (studentId == null || studentId.isBlank()) {
                    studentId = currentUser; // Bind to authoritative identity
                } else if (!studentId.equalsIgnoreCase(currentUser)) {
                    boolean isOwnId = false;
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth != null && auth.getDetails() instanceof io.jsonwebtoken.Claims claims) {
                        String claimUserId = claims.get("userId", String.class);
                        Object claimId = claims.get("id");
                        if (claimUserId != null && studentId.equalsIgnoreCase(claimUserId)) {
                            isOwnId = true;
                        } else if (claimId != null && studentId.equalsIgnoreCase(String.valueOf(claimId))) {
                            isOwnId = true;
                        }
                    }
                    if (!isOwnId) {
                        throw new AccessDeniedException("Students can only enroll themselves");
                    }
                }
            }
        }

        // 1. Validate student
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("studentId is required");
        }
        Boolean studentExists;
        try {
            studentExists = userClient.existsById(studentId).getBody();
        } catch (Exception e) {
            throw new RuntimeException("User Service unavailable: " + e.getMessage());
        }
        if (studentExists == null || !studentExists) {
            throw new IllegalArgumentException("Student '" + studentId + "' does not exist. Please create the student first.");
        }

        // 2. Validate course
        if (request.getCourseId() == null) {
            throw new IllegalArgumentException("courseId is required");
        }
        CourseClientResponse course;
        try {
            course = courseClient.getCourseById(request.getCourseId());
        } catch (Exception e) {
            throw new IllegalArgumentException("Course '" + request.getCourseId() + "' does not exist.");
        }
        if (course == null) {
            throw new IllegalArgumentException("Course '" + request.getCourseId() + "' does not exist.");
        }

        // 3. Check for existing enrollment for this student and course
        Optional<Enrollment> existingOpt = enrollmentRepository.findByStudentIdAndCourseId(studentId, request.getCourseId());
        Enrollment enrollment;

        if (existingOpt.isPresent()) {
            Enrollment existing = existingOpt.get();
            // Active or pending enrollment already exists
            if ("ENROLLED".equalsIgnoreCase(existing.getStatus()) || "COMPLETED".equalsIgnoreCase(existing.getStatus())) {
                throw new IllegalStateException("Student '" + studentId + "' is already enrolled in course " + request.getCourseId());
            } else if ("PENDING_PAYMENT".equalsIgnoreCase(existing.getStatus())) {
                if (request.getIdempotencyKey() != null && request.getIdempotencyKey().equals(existing.getIdempotencyKey())) {
                    return convertToResponse(existing);
                }
                throw new IllegalStateException("Student '" + studentId + "' already has a pending enrollment in course " + request.getCourseId() + ". Please complete tuition payment.");
            } else {
                // PAYMENT_FAILED or CANCELLED: reuse existing row for retry
                enrollment = existing;
            }
        } else {
            enrollment = new Enrollment();
            enrollment.setStudentId(studentId);
            enrollment.setCourseId(request.getCourseId());
        }

        String idempotencyKey = request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()
                ? request.getIdempotencyKey()
                : "ENR-" + UUID.randomUUID();
        enrollment.setIdempotencyKey(idempotencyKey);

        // 4. STEP 1: Atomically reserve seat in Course Service
        try {
            courseClient.reserveSeat(request.getCourseId());
            enrollment.setSeatReserved(true);
        } catch (Exception e) {
            throw new IllegalStateException("Course capacity reached or registration closed: " + e.getMessage());
        }

        // 5. STEP 2: Persist enrollment in PENDING_PAYMENT
        enrollment.setStatus("PENDING_PAYMENT");
        enrollment.setFailureReason(null);
        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);

        // If autoPay is not requested, return the PENDING_PAYMENT enrollment with seat reserved
        if (!Boolean.TRUE.equals(request.getAutoPay())) {
            return convertToResponse(savedEnrollment);
        }

        // 6. STEP 3: Auto-Initiate Payment with Payment Service (if autoPay requested)
        PaymentClientResponse paymentResponse = null;
        try {
            PaymentClientRequest paymentRequest = new PaymentClientRequest();
            paymentRequest.setEnrollmentId(savedEnrollment.getId());
            paymentRequest.setAmount(defaultTuitionAmount);
            paymentRequest.setIdempotencyKey("PAY-" + savedEnrollment.getId() + "-" + idempotencyKey);
            paymentRequest.setTestScenario(request.getTestScenario());
            paymentRequest.setPaymentMethod("CARD");

            paymentResponse = paymentClient.createPayment(paymentRequest);

        } catch (Exception e) {
            log.error("Payment Service error or timeout during enrollment {}: {}", savedEnrollment.getId(), e.getMessage());
            savedEnrollment.setFailureReason("Payment pending reconciliation: " + e.getMessage());
            enrollmentRepository.save(savedEnrollment);
            throw new RuntimeException("Payment processing error or timeout. Enrollment is held in PENDING_PAYMENT for recovery: " + e.getMessage());
        }

        // 7. STEP 4: Evaluate Payment Outcome
        if (paymentResponse != null && ("SUCCESS".equalsIgnoreCase(paymentResponse.getPaymentStatus()) || "PAID".equalsIgnoreCase(paymentResponse.getPaymentStatus()))) {
            savedEnrollment.setStatus("ENROLLED");
            savedEnrollment.setFailureReason(null);
            Enrollment confirmedEnrollment = enrollmentRepository.save(savedEnrollment);
            return convertToResponse(confirmedEnrollment);

        } else if (paymentResponse != null && "FAILED".equalsIgnoreCase(paymentResponse.getPaymentStatus())) {
            compensateSeatReservation(savedEnrollment, "Payment was rejected by provider");
            savedEnrollment.setStatus("PAYMENT_FAILED");
            savedEnrollment.setFailureReason("Payment was rejected by provider");
            enrollmentRepository.save(savedEnrollment);
            throw new IllegalStateException("Payment failed for enrollment: Payment rejected by provider");

        } else {
            savedEnrollment.setFailureReason("Payment status unknown. Awaiting reconciliation.");
            enrollmentRepository.save(savedEnrollment);
            return convertToResponse(savedEnrollment);
        }
    }

    // ==========================================
    // CONFIRM PAYMENT NOTIFICATION FROM PAYMENT SERVICE
    // ==========================================
    @Transactional
    public EnrollmentResponse confirmPayment(Long id, String paymentStatus) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found with id: " + id));

        if ("SUCCESS".equalsIgnoreCase(paymentStatus) || "PAID".equalsIgnoreCase(paymentStatus)) {
            enrollment.setStatus("ENROLLED");
            enrollment.setFailureReason(null);
        } else if ("FAILED".equalsIgnoreCase(paymentStatus)) {
            compensateSeatReservation(enrollment, "Payment was rejected by provider");
            enrollment.setStatus("PAYMENT_FAILED");
            enrollment.setFailureReason("Payment was rejected by provider");
        } else {
            enrollment.setFailureReason("Payment outcome: " + paymentStatus);
        }
        return convertToResponse(enrollmentRepository.save(enrollment));
    }

    // ==========================================
    // SAGA COMPENSATION: RELEASE SEAT
    // ==========================================
    private void compensateSeatReservation(Enrollment enrollment, String reason) {
        if (enrollment.isSeatReserved()) {
            try {
                courseClient.releaseSeat(enrollment.getCourseId());
                enrollment.setSeatReserved(false);
                log.info("Compensated seat reservation for course {} on enrollment {}", enrollment.getCourseId(), enrollment.getId());
            } catch (Exception e) {
                log.error("Failed to release seat for course {} during compensation: {}", enrollment.getCourseId(), e.getMessage());
            }
        }
    }

    // ==========================================
    // SAGA CANCEL & COMPENSATE
    // ==========================================
    @Transactional
    public EnrollmentResponse cancelEnrollment(Long id, String reason) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found with id: " + id));

        // Enforce ownership: Students can only cancel their own enrollments
        if (isCurrentUserStudent()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && !enrollment.getStudentId().equalsIgnoreCase(currentUser)) {
                throw new AccessDeniedException("Students can only cancel their own enrollments");
            }
        }

        compensateSeatReservation(enrollment, reason);
        enrollment.setStatus("CANCELLED");
        enrollment.setFailureReason(reason);
        return convertToResponse(enrollmentRepository.save(enrollment));
    }

    // ==========================================
    // SAGA RECONCILIATION
    // ==========================================
    @Transactional
    public EnrollmentResponse reconcileEnrollment(Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found with id: " + id));

        if ("ENROLLED".equalsIgnoreCase(enrollment.getStatus()) || "CANCELLED".equalsIgnoreCase(enrollment.getStatus())) {
            return convertToResponse(enrollment);
        }

        try {
            PaymentClientResponse payment = paymentClient.reconcilePayment(id);
            if (payment != null && ("SUCCESS".equalsIgnoreCase(payment.getPaymentStatus()) || "PAID".equalsIgnoreCase(payment.getPaymentStatus()))) {
                enrollment.setStatus("ENROLLED");
                enrollment.setFailureReason(null);
            } else if (payment != null && "FAILED".equalsIgnoreCase(payment.getPaymentStatus())) {
                compensateSeatReservation(enrollment, "Payment confirmed failed during reconciliation");
                enrollment.setStatus("PAYMENT_FAILED");
                enrollment.setFailureReason("Payment confirmed failed during reconciliation");
            }
        } catch (Exception e) {
            log.warn("Reconciliation attempt for enrollment {} could not verify payment: {}", id, e.getMessage());
        }

        return convertToResponse(enrollmentRepository.save(enrollment));
    }

    // ==========================================
    // SCHEDULED RECOVERY WORKER
    // ==========================================
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void recoverPendingEnrollments() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(60);
        List<Enrollment> pending = enrollmentRepository.findByStatusAndCreatedAtBefore("PENDING_PAYMENT", cutoff);

        for (Enrollment enrollment : pending) {
            log.info("Running automated Saga recovery for pending enrollment {}", enrollment.getId());
            try {
                reconcileEnrollment(enrollment.getId());
            } catch (Exception e) {
                log.error("Recovery failed for enrollment {}: {}", enrollment.getId(), e.getMessage());
            }
        }
    }

    // READ ALL (Students see own; ADMIN sees all)
    public List<EnrollmentResponse> getAllEnrollments() {
        if (!isCurrentUserAdmin() && isCurrentUserStudent()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && !currentUser.isBlank()) {
                return enrollmentRepository.findByStudentId(currentUser).stream()
                        .map(this::convertToResponse)
                        .toList();
            }
        }
        return enrollmentRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
    }

    // READ BY ID
    public EnrollmentResponse getEnrollmentById(Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found with id: " + id));

        // Enforce ownership: Students can only view their own enrollments (ADMIN can view any)
        if (!isCurrentUserAdmin() && isCurrentUserStudent()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && !enrollment.getStudentId().equalsIgnoreCase(currentUser)) {
                throw new AccessDeniedException("Students can only view their own enrollments");
            }
        }

        return convertToResponse(enrollment);
    }

    // READ BY STUDENT ID
    public List<EnrollmentResponse> getEnrollmentsByStudent(String studentId) {
        // Enforce ownership: Students can only view their own enrollments (ADMIN can view any)
        if (!isCurrentUserAdmin() && isCurrentUserStudent()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && !currentUser.equalsIgnoreCase(studentId)) {
                throw new AccessDeniedException("Students can only view their own enrollments");
            }
        }

        return enrollmentRepository.findByStudentId(studentId).stream()
                .map(this::convertToResponse)
                .toList();
    }

    // UPDATE
    public EnrollmentResponse updateEnrollment(Long id, EnrollmentRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found with id: " + id));

        if (request.getStudentId() != null && !request.getStudentId().isBlank()) {
            enrollment.setStudentId(request.getStudentId());
        }
        if (request.getCourseId() != null) {
            enrollment.setCourseId(request.getCourseId());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            enrollment.setStatus(request.getStatus().toUpperCase());
        }

        return convertToResponse(enrollmentRepository.save(enrollment));
    }

    // DELETE
    public void deleteEnrollment(Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found with id: " + id));
        compensateSeatReservation(enrollment, "Enrollment deleted by administrator");
        enrollmentRepository.delete(enrollment);
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    public boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            boolean hasAdminAuthority = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
            if (hasAdminAuthority) {
                return true;
            }
            if (auth.getDetails() instanceof io.jsonwebtoken.Claims claims) {
                Object roleClaim = claims.get("role");
                if ("ADMIN".equalsIgnoreCase(String.valueOf(roleClaim)) || "ROLE_ADMIN".equalsIgnoreCase(String.valueOf(roleClaim))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCurrentUserStudent() {
        if (isCurrentUserAdmin()) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT") || a.getAuthority().equals("STUDENT"));
        }
        return false;
    }

    private EnrollmentResponse convertToResponse(Enrollment enrollment) {
        EnrollmentResponse response = new EnrollmentResponse();
        response.setId(enrollment.getId());
        response.setStudentId(enrollment.getStudentId());
        response.setCourseId(enrollment.getCourseId());
        response.setStatus(enrollment.getStatus());
        response.setSeatReserved(enrollment.isSeatReserved());
        response.setIdempotencyKey(enrollment.getIdempotencyKey());
        response.setFailureReason(enrollment.getFailureReason());
        response.setCreatedAt(enrollment.getCreatedAt());
        response.setUpdatedAt(enrollment.getUpdatedAt());
        return response;
    }
}