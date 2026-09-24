package soa.paymentservice.service;

import java.util.List;

import org.springframework.stereotype.Service;

import soa.paymentservice.client.EnrollmentClient;
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
        this.enrollmentClient = enrollmentClient;2
    }

    // CREATE - validates enrollment exists + prevents duplicate payment per enrollment
    public PaymentResponse createPayment(PaymentRequest request) {

        // 1. enrollmentId is required
        if (request.getEnrollmentId() == null) {
            throw new IllegalArgumentException("enrollmentId is required");
        }

        // 2. Validate enrollment exists via EnrollmentClient
        Boolean enrollmentExists;
        try {
            enrollmentExists = enrollmentClient.existsById(request.getEnrollmentId()).getBody();
        } catch (Exception e) {
            throw new RuntimeException("Enrollment Service unavailable: " + e.getMessage());
        }
        if (enrollmentExists == null || !enrollmentExists) {
            throw new IllegalArgumentException(
                    "Enrollment '" + request.getEnrollmentId() + "' does not exist.");
        }

        // 3. Check duplicate: one payment per enrollment only
        if (paymentRepository.existsByEnrollmentId(request.getEnrollmentId())) {
            throw new IllegalStateException(
                    "A payment already exists for enrollment '" + request.getEnrollmentId()
                    + "'. Retrieve the existing payment and update its status instead.");
        }

        Payment payment = new Payment();
        payment.setEnrollmentId(request.getEnrollmentId());
        payment.setAmount(request.getAmount());
        payment.setPaymentStatus(
                request.getPaymentStatus() != null ? request.getPaymentStatus() : "PENDING");

        Payment savedPayment = paymentRepository.save(payment);
        return convertToResponse(savedPayment);
    }

    // READ ALL
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
    }

    // READ BY PAYMENT ID
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found with id: " + id));
        return convertToResponse(payment);
    }

    // GET PAYMENT BY ENROLLMENT ID
    public PaymentResponse getPaymentByEnrollmentId(Long enrollmentId) {
        Payment payment = paymentRepository.findByEnrollmentId(enrollmentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "No payment found for enrollment: " + enrollmentId));
        return convertToResponse(payment);
    }

    // UPDATE - uses Payment ID, validates enrollment, prevents duplicate cross-assignment
    public PaymentResponse updatePayment(Long id, PaymentRequest request) {

        // 1. Find existing payment by payment ID
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found with id: " + id));

        // 2. If enrollmentId is being changed, validate new enrollment exists
        Long newEnrollmentId = request.getEnrollmentId();
        if (newEnrollmentId != null && !newEnrollmentId.equals(payment.getEnrollmentId())) {

            // Validate new enrollment exists
            Boolean enrollmentExists;
            try {
                enrollmentExists = enrollmentClient.existsById(newEnrollmentId).getBody();
            } catch (Exception e) {
                throw new RuntimeException("Enrollment Service unavailable: " + e.getMessage());
            }
            if (enrollmentExists == null || !enrollmentExists) {
                throw new IllegalArgumentException(
                        "Enrollment '" + newEnrollmentId + "' does not exist.");
            }

            // Prevent changing enrollment to one that already has a different payment
            if (paymentRepository.existsByEnrollmentId(newEnrollmentId)) {
                throw new IllegalStateException(
                        "Enrollment '" + newEnrollmentId
                        + "' already has a payment. Cannot reassign this payment to it.");
            }

            payment.setEnrollmentId(newEnrollmentId);
        }

        // 3. Apply other updates
        if (request.getAmount() != null) {
            payment.setAmount(request.getAmount());
        }
        if (request.getPaymentStatus() != null) {
            payment.setPaymentStatus(request.getPaymentStatus());
        }

        return convertToResponse(paymentRepository.save(payment));
    }

    // DELETE
    public void deletePayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found with id: " + id));
        paymentRepository.delete(payment);
    }

    private PaymentResponse convertToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setEnrollmentId(payment.getEnrollmentId());
        response.setAmount(payment.getAmount());
        response.setPaymentStatus(payment.getPaymentStatus());
        return response;
    }
}