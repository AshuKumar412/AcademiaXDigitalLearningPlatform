package soa.paymentservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import soa.paymentservice.client.EnrollmentClient;
import soa.paymentservice.dto.EnrollmentClientResponse;
import soa.paymentservice.dto.PaymentRequest;
import soa.paymentservice.dto.PaymentResponse;
import soa.paymentservice.model.Payment;
import soa.paymentservice.repository.PaymentRepository;
import soa.paymentservice.service.PaymentService;

class PaymentSecurityAuthorizationTest {

    private PaymentRepository paymentRepository;
    private EnrollmentClient enrollmentClient;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        enrollmentClient = mock(EnrollmentClient.class);
        paymentService = new PaymentService(paymentRepository, enrollmentClient);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void testGetPaymentById_OwnPayment_Succeeds() {
        authenticateAs("student_alice", "STUDENT");

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setEnrollmentId(50L);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setPaymentStatus("SUCCESS");

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        EnrollmentClientResponse enr = new EnrollmentClientResponse();
        enr.setId(50L);
        enr.setStudentId("student_alice"); // belongs to Alice
        when(enrollmentClient.getEnrollmentById(50L)).thenReturn(enr);

        PaymentResponse response = paymentService.getPaymentById(10L);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("SUCCESS", response.getPaymentStatus());
    }

    @Test
    void testGetPaymentById_AnotherStudentsPayment_ThrowsAccessDenied() {
        authenticateAs("student_alice", "STUDENT");

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setEnrollmentId(50L);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setPaymentStatus("SUCCESS");

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        EnrollmentClientResponse enr = new EnrollmentClientResponse();
        enr.setId(50L);
        enr.setStudentId("student_bob"); // belongs to Bob!
        when(enrollmentClient.getEnrollmentById(50L)).thenReturn(enr);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> paymentService.getPaymentById(10L));
        assertEquals("Students can only access payment records for their own enrollments", ex.getMessage());
    }

    @Test
    void testUpdatePayment_NonAdminAttemptingToMarkSuccess_ThrowsAccessDenied() {
        authenticateAs("student_alice", "STUDENT");

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setEnrollmentId(50L);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setPaymentStatus("PENDING");

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));

        PaymentRequest request = new PaymentRequest();
        request.setPaymentStatus("SUCCESS"); // Attacker attempting to mark paid

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> paymentService.updatePayment(10L, request));
        assertEquals("Clients cannot directly mark payment status as SUCCESS. Payments must be processed through valid provider channels.", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testUpdatePayment_AdminAllowed() {
        authenticateAs("admin_user", "ADMIN");

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setEnrollmentId(50L);
        payment.setAmount(new BigDecimal("500.00"));
        payment.setPaymentStatus("PENDING");

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentRequest request = new PaymentRequest();
        request.setPaymentStatus("SUCCESS");

        PaymentResponse response = paymentService.updatePayment(10L, request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getPaymentStatus());
    }

    @Test
    void testCreatePayment_StudentCanPayOwnEnrollment_Succeeds() {
        authenticateAs("student_alice", "STUDENT");

        EnrollmentClientResponse enr = new EnrollmentClientResponse();
        enr.setId(60L);
        enr.setStudentId("student_alice");
        enr.setStatus("PENDING_PAYMENT");
        when(enrollmentClient.getEnrollmentById(60L)).thenReturn(enr);

        when(paymentRepository.findByEnrollmentId(60L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) p.setId(101L);
            return p;
        });

        PaymentRequest request = new PaymentRequest();
        request.setEnrollmentId(60L);
        request.setAmount(new BigDecimal("500.00"));
        request.setIdempotencyKey("PAY-ALICE-60");

        PaymentResponse response = paymentService.createPayment(request);

        assertNotNull(response);
        assertEquals("SUCCESS", response.getPaymentStatus());
        assertEquals(60L, response.getEnrollmentId());
        verify(enrollmentClient).confirmPayment(60L, "SUCCESS");
    }

    @Test
    void testCreatePayment_StudentCannotPayOthersEnrollment_ThrowsAccessDenied() {
        authenticateAs("student_alice", "STUDENT");

        EnrollmentClientResponse enr = new EnrollmentClientResponse();
        enr.setId(61L);
        enr.setStudentId("student_bob"); // Bob's enrollment
        enr.setStatus("PENDING_PAYMENT");
        when(enrollmentClient.getEnrollmentById(61L)).thenReturn(enr);

        PaymentRequest request = new PaymentRequest();
        request.setEnrollmentId(61L);
        request.setAmount(new BigDecimal("500.00"));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> paymentService.createPayment(request));
        assertEquals("Students can only pay for their own enrollments", ex.getMessage());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testCreatePayment_AlreadyEnrolled_ThrowsIllegalStateException() {
        authenticateAs("student_alice", "STUDENT");

        EnrollmentClientResponse enr = new EnrollmentClientResponse();
        enr.setId(62L);
        enr.setStudentId("student_alice");
        enr.setStatus("ENROLLED"); // Already enrolled/paid
        when(enrollmentClient.getEnrollmentById(62L)).thenReturn(enr);

        PaymentRequest request = new PaymentRequest();
        request.setEnrollmentId(62L);
        request.setAmount(new BigDecimal("500.00"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> paymentService.createPayment(request));
        assertEquals("Enrollment '62' is already paid and completed.", ex.getMessage());
    }

    @Test
    void testCreatePayment_DuplicateSuccessPayment_ThrowsIllegalStateException() {
        authenticateAs("student_alice", "STUDENT");

        EnrollmentClientResponse enr = new EnrollmentClientResponse();
        enr.setId(63L);
        enr.setStudentId("student_alice");
        enr.setStatus("PENDING_PAYMENT");
        when(enrollmentClient.getEnrollmentById(63L)).thenReturn(enr);

        Payment existingSuccess = new Payment();
        existingSuccess.setId(201L);
        existingSuccess.setEnrollmentId(63L);
        existingSuccess.setPaymentStatus("SUCCESS");
        existingSuccess.setIdempotencyKey("KEY-1");
        when(paymentRepository.findByEnrollmentId(63L)).thenReturn(Optional.of(existingSuccess));

        PaymentRequest request = new PaymentRequest();
        request.setEnrollmentId(63L);
        request.setAmount(new BigDecimal("500.00"));
        request.setIdempotencyKey("KEY-2"); // different key, same enrollment already succeeded

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> paymentService.createPayment(request));
        assertEquals("A successful payment already exists for enrollment '63'.", ex.getMessage());
    }

    @Test
    void testCreatePayment_IdempotencyKeyReturnsExistingPayment() {
        Payment existingPayment = new Payment();
        existingPayment.setId(301L);
        existingPayment.setEnrollmentId(64L);
        existingPayment.setPaymentStatus("SUCCESS");
        existingPayment.setIdempotencyKey("IDEMP-KEY-99");
        when(paymentRepository.findByIdempotencyKey("IDEMP-KEY-99")).thenReturn(Optional.of(existingPayment));

        PaymentRequest request = new PaymentRequest();
        request.setEnrollmentId(64L);
        request.setIdempotencyKey("IDEMP-KEY-99");

        PaymentResponse response = paymentService.createPayment(request);

        assertNotNull(response);
        assertEquals(301L, response.getId());
        assertEquals("SUCCESS", response.getPaymentStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testDeletePayment_AdminAllowed() {
        authenticateAs("admin_user", "ADMIN");

        Payment payment = new Payment();
        payment.setId(401L);
        when(paymentRepository.findById(401L)).thenReturn(Optional.of(payment));

        paymentService.deletePayment(401L);
        verify(paymentRepository).delete(payment);
    }
}
