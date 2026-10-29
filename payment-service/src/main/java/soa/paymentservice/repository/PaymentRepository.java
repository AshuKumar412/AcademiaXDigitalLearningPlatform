package soa.paymentservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import soa.paymentservice.model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByEnrollmentId(Long enrollmentId);

    Optional<Payment> findByEnrollmentId(Long enrollmentId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}