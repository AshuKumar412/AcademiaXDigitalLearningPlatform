package soa.enrollmentservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import soa.enrollmentservice.client.CourseClient;
import soa.enrollmentservice.client.PaymentClient;
import soa.enrollmentservice.client.UserClient;
import soa.enrollmentservice.dto.CourseClientResponse;
import soa.enrollmentservice.dto.EnrollmentRequest;
import soa.enrollmentservice.dto.EnrollmentResponse;
import soa.enrollmentservice.dto.PaymentClientRequest;
import soa.enrollmentservice.dto.PaymentClientResponse;
import soa.enrollmentservice.model.Enrollment;
import soa.enrollmentservice.repository.EnrollmentRepository;
import soa.enrollmentservice.service.EnrollmentService;

class EnrollmentSagaServiceTest {

    private EnrollmentRepository enrollmentRepository;
    private CourseClient courseClient;
    private PaymentClient paymentClient;
    private UserClient userClient;
    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentRepository = mock(EnrollmentRepository.class);
        courseClient = mock(CourseClient.class);
        paymentClient = mock(PaymentClient.class);
        userClient = mock(UserClient.class);
        enrollmentService = new EnrollmentService(enrollmentRepository, courseClient, paymentClient, userClient);
    }

    @Test
    void testSaga_SuccessfulPayment_ResultsInEnrolled() {
        String studentId = "2400032678";
        Long courseId = 4L;

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);
        request.setAutoPay(true);

        when(userClient.existsById(studentId)).thenReturn(ResponseEntity.ok(true));

        CourseClientResponse courseResponse = new CourseClientResponse();
        courseResponse.setId(courseId);
        courseResponse.setCapacity(30);
        when(courseClient.getCourseById(courseId)).thenReturn(courseResponse);

        when(enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());
        when(courseClient.reserveSeat(courseId)).thenReturn(ResponseEntity.ok(true));

        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> {
            Enrollment e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            return e;
        });

        PaymentClientResponse paymentResponse = new PaymentClientResponse();
        paymentResponse.setId(50L);
        paymentResponse.setEnrollmentId(10L);
        paymentResponse.setPaymentStatus("SUCCESS");
        when(paymentClient.createPayment(any(PaymentClientRequest.class))).thenReturn(paymentResponse);

        EnrollmentResponse response = enrollmentService.createEnrollment(request);

        assertNotNull(response);
        assertEquals("ENROLLED", response.getStatus());
        assertTrue(response.isSeatReserved());
        verify(courseClient, times(1)).reserveSeat(courseId);
        verify(courseClient, never()).releaseSeat(courseId);
    }

    @Test
    void testSaga_PaymentFailure_TriggersSeatCompensationAndMarksPaymentFailed() {
        String studentId = "2400032678";
        Long courseId = 4L;

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);
        request.setAutoPay(true);

        when(userClient.existsById(studentId)).thenReturn(ResponseEntity.ok(true));

        CourseClientResponse courseResponse = new CourseClientResponse();
        courseResponse.setId(courseId);
        courseResponse.setCapacity(30);
        when(courseClient.getCourseById(courseId)).thenReturn(courseResponse);

        when(enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());
        when(courseClient.reserveSeat(courseId)).thenReturn(ResponseEntity.ok(true));

        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> {
            Enrollment e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            return e;
        });

        PaymentClientResponse paymentResponse = new PaymentClientResponse();
        paymentResponse.setId(51L);
        paymentResponse.setEnrollmentId(10L);
        paymentResponse.setPaymentStatus("FAILED");
        when(paymentClient.createPayment(any(PaymentClientRequest.class))).thenReturn(paymentResponse);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> enrollmentService.createEnrollment(request));
        assertTrue(ex.getMessage().contains("Payment failed for enrollment"));

        // Verify that seat compensation was executed!
        verify(courseClient, times(1)).releaseSeat(courseId);
    }

    @Test
    void testSaga_PaymentTimeout_RetainsPendingPaymentWithoutReleasingSeat() {
        String studentId = "2400032678";
        Long courseId = 4L;

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);
        request.setAutoPay(true);

        when(userClient.existsById(studentId)).thenReturn(ResponseEntity.ok(true));

        CourseClientResponse courseResponse = new CourseClientResponse();
        courseResponse.setId(courseId);
        courseResponse.setCapacity(30);
        when(courseClient.getCourseById(courseId)).thenReturn(courseResponse);

        when(enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());
        when(courseClient.reserveSeat(courseId)).thenReturn(ResponseEntity.ok(true));

        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> {
            Enrollment e = inv.getArgument(0);
            if (e.getId() == null) e.setId(10L);
            return e;
        });

        // Simulate gateway timeout
        when(paymentClient.createPayment(any(PaymentClientRequest.class))).thenThrow(new RuntimeException("SocketTimeoutException: Read timed out"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> enrollmentService.createEnrollment(request));
        assertTrue(ex.getMessage().contains("PENDING_PAYMENT"));

        // Critical: seat must NOT be released during unknown/timeout state
        verify(courseClient, never()).releaseSeat(courseId);
    }

    @Test
    void testSaga_Reconciliation_RecoversLostResponseToEnrolled() {
        Enrollment pending = new Enrollment();
        pending.setId(20L);
        pending.setCourseId(4L);
        pending.setStudentId("2400032678");
        pending.setStatus("PENDING_PAYMENT");
        pending.setSeatReserved(true);

        when(enrollmentRepository.findById(20L)).thenReturn(Optional.of(pending));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        PaymentClientResponse payment = new PaymentClientResponse();
        payment.setId(99L);
        payment.setEnrollmentId(20L);
        payment.setPaymentStatus("SUCCESS");
        when(paymentClient.reconcilePayment(20L)).thenReturn(payment);

        EnrollmentResponse response = enrollmentService.reconcileEnrollment(20L);

        assertEquals("ENROLLED", response.getStatus());
        assertTrue(response.isSeatReserved());
        verify(courseClient, never()).releaseSeat(4L);
    }

    @Test
    void testSaga_RepeatedCompensation_IsIdempotent() {
        Enrollment cancelled = new Enrollment();
        cancelled.setId(30L);
        cancelled.setCourseId(4L);
        cancelled.setStudentId("2400032678");
        cancelled.setStatus("CANCELLED");
        cancelled.setSeatReserved(false); // already compensated

        when(enrollmentRepository.findById(30L)).thenReturn(Optional.of(cancelled));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = enrollmentService.cancelEnrollment(30L, "Cancelled again");

        assertEquals("CANCELLED", response.getStatus());
        assertFalse(response.isSeatReserved());
        // releaseSeat should NOT be called again if seatReserved is already false
        verify(courseClient, never()).releaseSeat(4L);
    }

    @Test
    void testSaga_DuplicateEnrollment_Rejected() {
        String studentId = "2400032678";
        Long courseId = 4L;

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);

        when(userClient.existsById(studentId)).thenReturn(ResponseEntity.ok(true));

        CourseClientResponse courseResponse = new CourseClientResponse();
        courseResponse.setId(courseId);
        courseResponse.setCapacity(30);
        when(courseClient.getCourseById(courseId)).thenReturn(courseResponse);

        Enrollment existingEnrolled = new Enrollment();
        existingEnrolled.setId(40L);
        existingEnrolled.setStudentId(studentId);
        existingEnrolled.setCourseId(courseId);
        existingEnrolled.setStatus("ENROLLED");
        when(enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.of(existingEnrolled));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> enrollmentService.createEnrollment(request));
        assertTrue(ex.getMessage().contains("already enrolled"));

        // No seat reserved and no payment initiated
        verify(courseClient, never()).reserveSeat(any());
        verify(paymentClient, never()).createPayment(any());
    }

    @Test
    void testSaga_CourseCapacityExceeded_BlocksEnrollment() {
        String studentId = "2400032678";
        Long courseId = 4L;

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);

        when(userClient.existsById(studentId)).thenReturn(ResponseEntity.ok(true));

        CourseClientResponse courseResponse = new CourseClientResponse();
        courseResponse.setId(courseId);
        courseResponse.setCapacity(30);
        when(courseClient.getCourseById(courseId)).thenReturn(courseResponse);

        when(enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());
        // Course capacity reached in Course Service
        when(courseClient.reserveSeat(courseId)).thenThrow(new IllegalStateException("Course capacity reached. Maximum capacity is 30"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> enrollmentService.createEnrollment(request));
        assertTrue(ex.getMessage().contains("Course capacity reached"));

        // No payment initiated
        verify(paymentClient, never()).createPayment(any());
    }

    @Test
    void testCreateEnrollment_DecoupledMode_ReturnsPendingPaymentWithSeatReserved() {
        String studentId = "STU101";
        Long courseId = 1L;

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);
        // autoPay is false by default

        when(userClient.existsById(studentId)).thenReturn(ResponseEntity.ok(true));

        CourseClientResponse courseResponse = new CourseClientResponse();
        courseResponse.setId(courseId);
        courseResponse.setCapacity(25);
        when(courseClient.getCourseById(courseId)).thenReturn(courseResponse);

        when(enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.empty());
        when(courseClient.reserveSeat(courseId)).thenReturn(ResponseEntity.ok(true));

        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> {
            Enrollment e = inv.getArgument(0);
            if (e.getId() == null) e.setId(77L);
            return e;
        });

        EnrollmentResponse response = enrollmentService.createEnrollment(request);

        assertNotNull(response);
        assertEquals(77L, response.getId());
        assertEquals("PENDING_PAYMENT", response.getStatus());
        assertTrue(response.isSeatReserved());
        verify(courseClient, times(1)).reserveSeat(courseId);
        verify(paymentClient, never()).createPayment(any());
    }

    @Test
    void testConfirmPayment_Success_TransitionsToEnrolled() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(88L);
        enrollment.setCourseId(2L);
        enrollment.setStudentId("STU101");
        enrollment.setStatus("PENDING_PAYMENT");
        enrollment.setSeatReserved(true);

        when(enrollmentRepository.findById(88L)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = enrollmentService.confirmPayment(88L, "SUCCESS");

        assertNotNull(response);
        assertEquals("ENROLLED", response.getStatus());
        assertTrue(response.isSeatReserved());
        verify(courseClient, never()).releaseSeat(any());
    }

    @Test
    void testConfirmPayment_Failed_CompensatesSeatAndTransitionsToPaymentFailed() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(89L);
        enrollment.setCourseId(3L);
        enrollment.setStudentId("STU102");
        enrollment.setStatus("PENDING_PAYMENT");
        enrollment.setSeatReserved(true);

        when(enrollmentRepository.findById(89L)).thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = enrollmentService.confirmPayment(89L, "FAILED");

        assertNotNull(response);
        assertEquals("PAYMENT_FAILED", response.getStatus());
        assertFalse(response.isSeatReserved());
        verify(courseClient, times(1)).releaseSeat(3L);
    }

    @Test
    void testDuplicatePendingEnrollment_Rejected() {
        String studentId = "STU103";
        Long courseId = 5L;

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);

        when(userClient.existsById(studentId)).thenReturn(ResponseEntity.ok(true));

        CourseClientResponse courseResponse = new CourseClientResponse();
        courseResponse.setId(courseId);
        courseResponse.setCapacity(20);
        when(courseClient.getCourseById(courseId)).thenReturn(courseResponse);

        Enrollment existingPending = new Enrollment();
        existingPending.setId(90L);
        existingPending.setStudentId(studentId);
        existingPending.setCourseId(courseId);
        existingPending.setStatus("PENDING_PAYMENT");
        when(enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.of(existingPending));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> enrollmentService.createEnrollment(request));
        assertTrue(ex.getMessage().contains("already has a pending enrollment"));

        verify(courseClient, never()).reserveSeat(any());
    }

    @Test
    void testReEnrollmentAfterCancellation_Allowed() {
        String studentId = "STU104";
        Long courseId = 6L;

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentId);
        request.setCourseId(courseId);

        when(userClient.existsById(studentId)).thenReturn(ResponseEntity.ok(true));

        CourseClientResponse courseResponse = new CourseClientResponse();
        courseResponse.setId(courseId);
        courseResponse.setCapacity(20);
        when(courseClient.getCourseById(courseId)).thenReturn(courseResponse);

        // Previous enrollment was cancelled
        Enrollment existingCancelled = new Enrollment();
        existingCancelled.setId(91L);
        existingCancelled.setStudentId(studentId);
        existingCancelled.setCourseId(courseId);
        existingCancelled.setStatus("CANCELLED");
        existingCancelled.setSeatReserved(false);
        when(enrollmentRepository.findByStudentIdAndCourseId(studentId, courseId)).thenReturn(Optional.of(existingCancelled));
        when(courseClient.reserveSeat(courseId)).thenReturn(ResponseEntity.ok(true));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        EnrollmentResponse response = enrollmentService.createEnrollment(request);

        assertNotNull(response);
        assertEquals("PENDING_PAYMENT", response.getStatus());
        assertTrue(response.isSeatReserved());
        verify(courseClient, times(1)).reserveSeat(courseId);
    }
}
