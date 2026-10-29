package soa.courseservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import soa.courseservice.client.UserClient;
import soa.courseservice.dto.CourseRequest;
import soa.courseservice.dto.CourseResponse;
import soa.courseservice.model.Course;
import soa.courseservice.repository.CourseRepository;
import soa.courseservice.service.CourseService;

class CourseSeatReservationTest {

    private CourseRepository courseRepository;
    private UserClient userClient;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        userClient = mock(UserClient.class);
        courseService = new CourseService(courseRepository, userClient);
    }

    @Test
    void testReserveSeat_Success() {
        Course course = new Course();
        course.setId(10L);
        course.setTitle("Distributed Systems");
        course.setCapacity(30);
        course.setEnrolledCount(10);
        course.setStatus("OPEN");

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(courseRepository.atomicReserveSeat(10L)).thenReturn(1);

        boolean result = courseService.reserveSeat(10L);
        assertTrue(result, "Seat reservation should succeed when capacity is available and course is open");
    }

    @Test
    void testReserveSeat_CourseClosed() {
        Course course = new Course();
        course.setId(10L);
        course.setCapacity(30);
        course.setEnrolledCount(10);
        course.setStatus("CLOSED");

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> courseService.reserveSeat(10L));
        assertTrue(ex.getMessage().contains("registration is closed"));
    }

    @Test
    void testReserveSeat_DeadlinePassed() {
        Course course = new Course();
        course.setId(10L);
        course.setCapacity(30);
        course.setEnrolledCount(10);
        course.setStatus("OPEN");
        course.setEnrollmentDeadline(LocalDateTime.now().minusDays(1)); // passed

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> courseService.reserveSeat(10L));
        assertTrue(ex.getMessage().contains("deadline has passed"));
    }

    @Test
    void testReserveSeat_CapacityExceeded() {
        Course course = new Course();
        course.setId(10L);
        course.setCapacity(30);
        course.setEnrolledCount(30);
        course.setStatus("OPEN");

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(courseRepository.atomicReserveSeat(10L)).thenReturn(0); // atomic update returns 0 rows updated

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> courseService.reserveSeat(10L));
        assertTrue(ex.getMessage().contains("capacity reached"));
    }

    @Test
    void testReleaseSeat_Compensation() {
        Course course = new Course();
        course.setId(10L);
        course.setCapacity(30);
        course.setEnrolledCount(10);

        when(courseRepository.findById(10L)).thenReturn(Optional.of(course));
        when(courseRepository.atomicReleaseSeat(10L)).thenReturn(1);

        boolean result = courseService.releaseSeat(10L);
        assertTrue(result, "Seat release compensation should succeed");
    }
}
