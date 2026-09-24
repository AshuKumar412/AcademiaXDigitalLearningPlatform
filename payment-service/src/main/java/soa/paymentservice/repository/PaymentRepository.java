package soa.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import soa.paymentservice.model.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByEnrollmentId(Long enrollmentId);

    java.util.Optional<Payment> findByEnrollmentId(Long enrollmentId);
}