package soa.courseservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import soa.courseservice.model.Course;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByInstructorId(String instructorId);

    @Modifying
    @Query("UPDATE Course c SET c.enrolledCount = c.enrolledCount + 1 " +
           "WHERE c.id = :id AND c.status = 'OPEN' " +
           "AND (c.enrollmentDeadline IS NULL OR c.enrollmentDeadline > CURRENT_TIMESTAMP) " +
           "AND c.enrolledCount < c.capacity")
    int atomicReserveSeat(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Course c SET c.enrolledCount = c.enrolledCount - 1 " +
           "WHERE c.id = :id AND c.enrolledCount > 0")
    int atomicReleaseSeat(@Param("id") Long id);
}