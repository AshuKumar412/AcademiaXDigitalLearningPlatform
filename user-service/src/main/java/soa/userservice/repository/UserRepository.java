package soa.userservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import soa.userservice.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUserId(String userId);

    boolean existsByUserId(String userId);

    Optional<User> findByStudentId(String studentId);

    boolean existsByStudentId(String studentId);

    Optional<User> findByInstructorId(String instructorId);

    boolean existsByInstructorId(String instructorId);
}