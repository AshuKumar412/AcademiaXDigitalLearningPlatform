package soa.enrollmentservice.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import soa.enrollmentservice.client.CourseClient;
import soa.enrollmentservice.client.PaymentClient;
import soa.enrollmentservice.client.UserClient;
import soa.enrollmentservice.dto.CourseClientResponse;
import soa.enrollmentservice.dto.EnrollmentRequest;
import soa.enrollmentservice.dto.EnrollmentResponse;
import soa.enrollmentservice.dto.PaymentClientRequest;
import soa.enrollmentservice.exception.EnrollmentNotFoundException;
import soa.enrollmentservice.model.Enrollment;
import soa.enrollmentservice.repository.EnrollmentRepository;

@Service
public class EnrollmentService {

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

    // CREATE
    public EnrollmentResponse createEnrollment(EnrollmentRequest request) {

        // 1. Validate student exists
        if (request.getStudentId() == null || request.getStudentId().isBlank()) {
            throw new IllegalArgumentException("studentId is required");
        }
        Boolean studentExists;
        try {
            studentExists = userClient.existsById(request.getStudentId()).getBody();
        } catch (Exception e) {
            throw new RuntimeException("User Service unavailable: " + e.getMessage());
        }
        if (studentExists == null || !studentExists) {
            throw new IllegalArgumentException("Student '" + request.getStudentId() + "' does not exist. Please create the student first.");
        }

        // 2. Validate course exists
        CourseClientResponse course;
        try {
            course = courseClient.getCourseById(request.getCourseId());
        } catch (Exception e) {
            throw new IllegalArgumentException("Course '" + request.getCourseId() + "' does not exist.");
        }
        if (course == null) {
            throw new IllegalArgumentException("Course '" + request.getCourseId() + "' does not exist.");
        }

        // 3. Check duplicate enrollment
        if (enrollmentRepository.existsByStudentIdAndCourseId(request.getStudentId(), request.getCourseId())) {
            throw new IllegalStateException("Student '" + request.getStudentId() + "' is already enrolled in course " + request.getCourseId());
        }

        // 4. Check capacity
        if (course.getCapacity() > 0) {
            long currentEnrolled = enrollmentRepository.countByCourseId(request.getCourseId());
            if (currentEnrolled >= course.getCapacity()) {
                throw new IllegalStateException("Course capacity reached. Maximum capacity is " + course.getCapacity());
            }
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(request.getStudentId());
        enrollment.setCourseId(request.getCourseId());
        enrollment.setStatus(request.getStatus() != null ? request.getStatus() : "ENROLLED");

        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);

        // 5. Auto-initiate payment
        try {
            paymentClient.createPayment(new PaymentClientRequest(
                    savedEnrollment.getId(),
                    defaultTuitionAmount,
                    "PENDING"
            ));
        } catch (Exception e) {
            System.err.println("Payment initiation notice: " + e.getMessage());
        }

        return convertToResponse(savedEnrollment);
    }

    // READ ALL
    public List<EnrollmentResponse> getAllEnrollments() {
        return enrollmentRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
    }

    // READ BY ID
    public EnrollmentResponse getEnrollmentById(Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found with id: " + id));
        return convertToResponse(enrollment);
    }

    // UPDATE
    public EnrollmentResponse updateEnrollment(Long id, EnrollmentRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found with id: " + id));

        enrollment.setStudentId(request.getStudentId());
        enrollment.setCourseId(request.getCourseId());
        enrollment.setStatus(request.getStatus());

        return convertToResponse(enrollmentRepository.save(enrollment));
    }

    // DELETE
    public void deleteEnrollment(Long id) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException("Enrollment not found with id: " + id));
        enrollmentRepository.delete(enrollment);
    }

    private EnrollmentResponse convertToResponse(Enrollment enrollment) {
        EnrollmentResponse response = new EnrollmentResponse();
        response.setId(enrollment.getId());
        response.setStudentId(enrollment.getStudentId());
        response.setCourseId(enrollment.getCourseId());
        response.setStatus(enrollment.getStatus());
        return response;
    }
}