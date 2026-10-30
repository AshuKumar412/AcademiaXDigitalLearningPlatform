package soa.paymentservice.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import soa.paymentservice.client.EnrollmentClient;
import soa.paymentservice.dto.EnrollmentClientResponse;
import soa.paymentservice.dto.PaymentRequest;
import soa.paymentservice.dto.PaymentResponse;
import soa.paymentservice.exception.PaymentNotFoundException;
import soa.paymentservice.model.Payment;
import soa.paymentservice.repository.PaymentRepository;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final EnrollmentClient enrollmentClient;

    public PaymentService(PaymentRepository paymentRepository, EnrollmentClient enrollmentClient) {
        this.paymentRepository = paymentRepository;
        this.enrollmentClient = enrollmentClient;
    }

    // CREATE - validates enrollment exists + enforces student ownership, checks status, prevents duplicate payment, and notifies enrollment service
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        if (request.getEnrollmentId() == null) {
            throw new IllegalArgumentException("enrollmentId is required");
        }

        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existingByKey.isPresent()) {
                return convertToResponse(existingByKey.get());
            }
        }

        // 1. Verify that the enrollment exists
        EnrollmentClientResponse enrollment = null;
        try {
            enrollment = enrollmentClient.getEnrollmentById(request.getEnrollmentId());
        } catch (Exception ignored) {
        }
        if (enrollment == null) {
            Boolean exists = null;
            try {
                ResponseEntity<Boolean> resp = enrollmentClient.existsById(request.getEnrollmentId());
                exists = (resp != null) ? resp.getBody() : null;
            } catch (Exception ignored) {
            }
            if (exists == null || !exists) {
                throw new IllegalArgumentException("Enrollment '" + request.getEnrollmentId() + "' does not exist.");
            }
        }

        // 2. Verify that the enrollment belongs to the authenticated student (unless ADMIN)
        if (enrollment != null && !isCurrentUserAdmin() && isCurrentUserStudent()) {
            String currentStudent = getCurrentUser();
            if (currentStudent != null && enrollment.getStudentId() != null && !enrollment.getStudentId().equalsIgnoreCase(currentStudent)) {
                boolean isOwn = false;
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getDetails() instanceof io.jsonwebtoken.Claims claims) {
                    String claimUserId = claims.get("userId", String.class);
                    Object claimId = claims.get("id");
                    if (claimUserId != null && enrollment.getStudentId().equalsIgnoreCase(claimUserId)) {
                        isOwn = true;
                    } else if (claimId != null && enrollment.getStudentId().equalsIgnoreCase(String.valueOf(claimId))) {
                        isOwn = true;
                    }
                }
                if (!isOwn) {
                    throw new AccessDeniedException("Students can only pay for their own enrollments");
                }
            }
        }

        // 3. Verify that the enrollment is in PENDING_PAYMENT status
        if (enrollment != null) {
            if ("ENROLLED".equalsIgnoreCase(enrollment.getStatus()) || "COMPLETED".equalsIgnoreCase(enrollment.getStatus())) {
                throw new IllegalStateException("Enrollment '" + request.getEnrollmentId() + "' is already paid and completed.");
            }
            if ("CANCELLED".equalsIgnoreCase(enrollment.getStatus())) {
                throw new IllegalStateException("Enrollment '" + request.getEnrollmentId() + "' has been cancelled.");
            }
        }

        // 4. Prevent duplicate payment
        Optional<Payment> existingByEnrollment = paymentRepository.findByEnrollmentId(request.getEnrollmentId());
        if (existingByEnrollment.isPresent()) {
            Payment existingPayment = existingByEnrollment.get();
            if ("SUCCESS".equalsIgnoreCase(existingPayment.getPaymentStatus())) {
                if (request.getIdempotencyKey() != null && request.getIdempotencyKey().equals(existingPayment.getIdempotencyKey())) {
                    return convertToResponse(existingPayment);
                }
                throw new IllegalStateException(
                        "A successful payment already exists for enrollment '" + request.getEnrollmentId() + "'.");
            }
        }

        String scenario = request.getTestScenario();
        if ("TIMEOUT".equalsIgnoreCase(scenario)) {
            throw new RuntimeException("Payment gateway timed out during processing for enrollment " + request.getEnrollmentId());
        }

        Payment payment = existingByEnrollment.orElseGet(Payment::new);
        payment.setEnrollmentId(request.getEnrollmentId());
        payment.setAmount(request.getAmount());
        payment.setIdempotencyKey(request.getIdempotencyKey() != null ? request.getIdempotencyKey() : UUID.randomUUID().toString());
        payment.setTransactionId("TXN-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 9000 + 1000));
        payment.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "CARD");

        String status;
        if ("FAIL".equalsIgnoreCase(scenario) || "PAYMENT_REJECTED".equalsIgnoreCase(scenario)) {
            status = "FAILED";
        } else if ("UNKNOWN".equalsIgnoreCase(scenario)) {
            status = "UNKNOWN";
        } else {
            if (request.getPaymentStatus() != null && !request.getPaymentStatus().isBlank()) {
                status = normalizeStatus(request.getPaymentStatus());
            } else {
                status = "SUCCESS";
            }
        }
        payment.setPaymentStatus(status);

        Payment savedPayment = paymentRepository.save(payment);

        // 5. Notify Enrollment Service
        try {
            enrollmentClient.confirmPayment(request.getEnrollmentId(), status);
        } catch (Exception e) {
            // Inter-service call notice logged
        }

        return convertToResponse(savedPayment);
    }

    // READ ALL (Admin only via SecurityConfig)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
    }

    // READ BY PAYMENT ID
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));

        checkPaymentOwnership(payment);
        return convertToResponse(payment);
    }

    // GET PAYMENT BY ENROLLMENT ID
    public PaymentResponse getPaymentByEnrollmentId(Long enrollmentId) {
        Payment payment = paymentRepository.findByEnrollmentId(enrollmentId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment found for enrollment: " + enrollmentId));

        checkPaymentOwnership(payment);
        return convertToResponse(payment);
    }

    // GET PAYMENT BY IDEMPOTENCY KEY
    public PaymentResponse getPaymentByIdempotencyKey(String idempotencyKey) {
        Payment payment = paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new PaymentNotFoundException("No payment found with idempotency key: " + idempotencyKey));

        checkPaymentOwnership(payment);
        return convertToResponse(payment);
    }

    // RECONCILE PAYMENT (Saga recovery for unknown outcomes)
    @Transactional
    public PaymentResponse reconcilePayment(Long enrollmentId) {
        Payment payment = paymentRepository.findByEnrollmentId(enrollmentId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment found for enrollment: " + enrollmentId));

        if ("UNKNOWN".equalsIgnoreCase(payment.getPaymentStatus()) || "PENDING".equalsIgnoreCase(payment.getPaymentStatus())) {
            payment.setPaymentStatus("SUCCESS");
            payment = paymentRepository.save(payment);
        }
        return convertToResponse(payment);
    }

    // UPDATE - restricted to administrative or verified transactions
    @Transactional
    public PaymentResponse updatePayment(Long id, PaymentRequest request) {

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));

        // Prevent non-admin clients from setting status to SUCCESS directly
        if (!isCurrentUserAdmin() && request.getPaymentStatus() != null) {
            throw new AccessDeniedException("Clients cannot directly mark payment status as SUCCESS. Payments must be processed through valid provider channels.");
        }

        Long newEnrollmentId = request.getEnrollmentId();
        if (newEnrollmentId != null && !newEnrollmentId.equals(payment.getEnrollmentId())) {

            Boolean enrollmentExists;
            try {
                enrollmentExists = enrollmentClient.existsById(newEnrollmentId).getBody();
            } catch (Exception e) {
                throw new RuntimeException("Enrollment Service unavailable: " + e.getMessage());
            }
            if (enrollmentExists == null || !enrollmentExists) {
                throw new IllegalArgumentException("Enrollment '" + newEnrollmentId + "' does not exist.");
            }

            if (paymentRepository.existsByEnrollmentId(newEnrollmentId)) {
                throw new IllegalStateException(
                        "Enrollment '" + newEnrollmentId + "' already has a payment. Cannot reassign this payment to it.");
            }

            payment.setEnrollmentId(newEnrollmentId);
        }

        if (request.getAmount() != null) {
            payment.setAmount(request.getAmount());
        }
        if (request.getPaymentStatus() != null) {
            payment.setPaymentStatus(normalizeStatus(request.getPaymentStatus()));
        }
        if (request.getPaymentMethod() != null) {
            payment.setPaymentMethod(request.getPaymentMethod());
        }

        return convertToResponse(paymentRepository.save(payment));
    }

    // DELETE
    public void deletePayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + id));
        paymentRepository.delete(payment);
    }

    private void checkPaymentOwnership(Payment payment) {
        if (isCurrentUserAdmin()) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT") || a.getAuthority().equals("STUDENT"))) {
            String currentStudent = auth.getName();
            try {
                EnrollmentClientResponse enr = enrollmentClient.getEnrollmentById(payment.getEnrollmentId());
                if (enr != null && enr.getStudentId() != null && !enr.getStudentId().equalsIgnoreCase(currentStudent)) {
                    boolean isOwn = false;
                    if (auth.getDetails() instanceof io.jsonwebtoken.Claims claims) {
                        String claimUserId = claims.get("userId", String.class);
                        Object claimId = claims.get("id");
                        if (claimUserId != null && enr.getStudentId().equalsIgnoreCase(claimUserId)) {
                            isOwn = true;
                        } else if (claimId != null && enr.getStudentId().equalsIgnoreCase(String.valueOf(claimId))) {
                            isOwn = true;
                        }
                    }
                    if (!isOwn) {
                        throw new AccessDeniedException("Students can only access payment records for their own enrollments");
                    }
                }
            } catch (AccessDeniedException ade) {
                throw ade;
            } catch (Exception ignored) {
            }
        }
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

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
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

    private String normalizeStatus(String status) {
        if ("PAID".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status)) {
            return "SUCCESS";
        }
        return status.toUpperCase();
    }

    private PaymentResponse convertToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setEnrollmentId(payment.getEnrollmentId());
        response.setAmount(payment.getAmount());
        response.setPaymentStatus(payment.getPaymentStatus());
        response.setIdempotencyKey(payment.getIdempotencyKey());
        response.setTransactionId(payment.getTransactionId());
        response.setPaymentMethod(payment.getPaymentMethod());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        return response;
    }
}