package soa.paymentservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import soa.paymentservice.client.EnrollmentClient;
import soa.paymentservice.dto.PaymentRequest;
import soa.paymentservice.dto.PaymentResponse;
import soa.paymentservice.model.Payment;
import soa.paymentservice.repository.PaymentRepository;
import soa.paymentservice.service.PaymentService;

class PaymentIdempotencyTest {

    private PaymentRepository paymentRepository;
    private EnrollmentClient enrollmentClient;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        enrollmentClient = mock(EnrollmentClient.class);
        paymentService = new PaymentService(paymentRepository, enrollmentClient);
    }

    @Test
    void testCreatePayment_Success() {
        PaymentRequest request = new PaymentRequest();
        request.setEnrollmentId(100L);
        request.setAmount(new BigDecimal("500.00"));
        request.setIdempotencyKey("KEY-100");

        when(enrollmentClient.existsById(100L)).thenReturn(ResponseEntity.ok(true));
        when(paymentRepository.findByIdempotencyKey("KEY-100")).thenReturn(Optional.empty());
        when(paymentRepository.findByEnrollmentId(100L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("SUCCESS", response.getPaymentStatus());
        assertNotNull(response.getTransactionId());
        assertEquals("KEY-100", response.getIdempotencyKey());
    }

    @Test
    void testCreatePayment_IdempotentDuplicateReturnsExisting() {
        Payment existingPayment = new Payment();
        existingPayment.setId(1L);
        existingPayment.setEnrollmentId(100L);
        existingPayment.setAmount(new BigDecimal("500.00"));
        existingPayment.setPaymentStatus("SUCCESS");
        existingPayment.setIdempotencyKey("KEY-100");
        existingPayment.setTransactionId("TXN-12345");

        PaymentRequest request = new PaymentRequest();
        request.setEnrollmentId(100L);
        request.setAmount(new BigDecimal("500.00"));
        request.setIdempotencyKey("KEY-100");

        // Idempotency lookup finds existing record
        when(paymentRepository.findByIdempotencyKey("KEY-100")).thenReturn(Optional.of(existingPayment));

        PaymentResponse response = paymentService.createPayment(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("TXN-12345", response.getTransactionId());
        // Verify save was never called again (no duplicate charge)
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testCreatePayment_TestScenarioFailure() {
        PaymentRequest request = new PaymentRequest();
        request.setEnrollmentId(100L);
        request.setAmount(new BigDecimal("500.00"));
        request.setIdempotencyKey("KEY-FAIL");
        request.setTestScenario("FAIL");

        when(enrollmentClient.existsById(100L)).thenReturn(ResponseEntity.ok(true));
        when(paymentRepository.findByIdempotencyKey("KEY-FAIL")).thenReturn(Optional.empty());
        when(paymentRepository.findByEnrollmentId(100L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(2L);
            return p;
        });

        PaymentResponse response = paymentService.createPayment(request);

        assertNotNull(response);
        assertEquals("FAILED", response.getPaymentStatus());
    }

    @Test
    void testCreatePayment_TestScenarioTimeout() {
        PaymentRequest request = new PaymentRequest();
        request.setEnrollmentId(100L);
        request.setAmount(new BigDecimal("500.00"));
        request.setIdempotencyKey("KEY-TIMEOUT");
        request.setTestScenario("TIMEOUT");

        when(enrollmentClient.existsById(100L)).thenReturn(ResponseEntity.ok(true));
        when(paymentRepository.findByIdempotencyKey("KEY-TIMEOUT")).thenReturn(Optional.empty());
        when(paymentRepository.findByEnrollmentId(100L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> paymentService.createPayment(request));
        assertTrue(ex.getMessage().contains("Payment gateway timed out"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void testReconcilePayment_TransitionsUnknownToSuccess() {
        Payment pendingPayment = new Payment();
        pendingPayment.setId(3L);
        pendingPayment.setEnrollmentId(101L);
        pendingPayment.setPaymentStatus("UNKNOWN");

        when(paymentRepository.findByEnrollmentId(101L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentResponse response = paymentService.reconcilePayment(101L);

        assertEquals("SUCCESS", response.getPaymentStatus());
    }
}
