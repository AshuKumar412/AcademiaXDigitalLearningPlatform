package soa.enrollmentservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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

class EnrollmentSecurityOwnershipTest {

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
    void testEnroll_StudentIdBoundToAuthenticatedUser() {
        authenticateAs("student_alice", "STUDENT");

        EnrollmentRequest request = new EnrollmentRequest();
        request.setCourseId(5L);

        when(userClient.existsById("student_alice")).thenReturn(ResponseEntity.ok(true));

        CourseClientResponse course = new CourseClientResponse();
        course.setId(5L);
        course.setCapacity(30);
        when(courseClient.getCourseById(5L)).thenReturn(course);
        when(courseClient.reserveSeat(5L)).thenReturn(ResponseEntity.ok(true));

        when(enrollmentRepository.findByStudentIdAndCourseId("student_alice", 5L)).thenReturn(Optional.empty());
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> {
            Enrollment e = inv.getArgument(0);
            e.setId(201L);
            return e;
        });

        PaymentClientResponse payment = new PaymentClientResponse();
        payment.setId(88L);
        payment.setEnrollmentId(201L);
        payment.setPaymentStatus("SUCCESS");
        when(paymentClient.createPayment(any(PaymentClientRequest.class))).thenReturn(payment);

        EnrollmentResponse response = enrollmentService.createEnrollment(request);

        assertNotNull(response);
        assertEquals("student_alice", response.getStudentId());
        assertEquals("ENROLLED", response.getStatus());
    }

    @Test
    void testEnroll_StudentAttemptingToEnrollDifferentStudent_ThrowsAccessDenied() {
        authenticateAs("student_alice", "STUDENT");

        EnrollmentRequest request = new EnrollmentRequest();
        request.setCourseId(5L);
        // Alice attempts to enroll Bob
        request.setStudentId("student_bob");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> enrollmentService.createEnrollment(request));
        assertEquals("Students can only enroll themselves", ex.getMessage());

        verify(courseClient, never()).reserveSeat(any());
        verify(paymentClient, never()).createPayment(any());
    }

    @Test
    void testGetEnrollmentsByStudent_AnotherStudent_ThrowsAccessDenied() {
        authenticateAs("student_alice", "STUDENT");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> enrollmentService.getEnrollmentsByStudent("student_bob"));
        assertEquals("Students can only view their own enrollments", ex.getMessage());
    }

    @Test
    void testCancelEnrollment_AnotherStudentsEnrollment_ThrowsAccessDenied() {
        authenticateAs("student_alice", "STUDENT");

        Enrollment enrollment = new Enrollment();
        enrollment.setId(301L);
        enrollment.setStudentId("student_bob"); // Bob's enrollment
        enrollment.setCourseId(5L);
        enrollment.setStatus("ENROLLED");
        enrollment.setSeatReserved(true);

        when(enrollmentRepository.findById(301L)).thenReturn(Optional.of(enrollment));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> enrollmentService.cancelEnrollment(301L, "Attacker cancelling"));
        assertEquals("Students can only cancel their own enrollments", ex.getMessage());

        // Seat should NOT be released by unauthorized caller
        verify(courseClient, never()).releaseSeat(any());
    }

    @Test
    void testGetEnrollmentById_AnotherStudentsEnrollment_ThrowsAccessDenied() {
        authenticateAs("student_alice", "STUDENT");

        Enrollment enrollment = new Enrollment();
        enrollment.setId(301L);
        enrollment.setStudentId("student_bob");

        when(enrollmentRepository.findById(301L)).thenReturn(Optional.of(enrollment));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> enrollmentService.getEnrollmentById(301L));
        assertEquals("Students can only view their own enrollments", ex.getMessage());
    }
}
