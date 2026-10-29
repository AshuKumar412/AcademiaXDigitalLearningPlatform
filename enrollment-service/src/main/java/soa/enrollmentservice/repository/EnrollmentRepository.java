package soa.enrollmentservice.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import soa.enrollmentservice.model.Enrollment;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByCourseId(Long courseId);

    boolean existsByStudentIdAndCourseId(String studentId, Long courseId);

    Optional<Enrollment> findByStudentIdAndCourseId(String studentId, Long courseId);

    List<Enrollment> findByStudentId(String studentId);

    List<Enrollment> findByCourseId(Long courseId);

    Optional<Enrollment> findByIdempotencyKey(String idempotencyKey);

    List<Enrollment> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);
}