package soa.courseservice;

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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import soa.courseservice.client.UserClient;
import soa.courseservice.dto.CourseRequest;
import soa.courseservice.dto.CourseResponse;
import soa.courseservice.model.Course;
import soa.courseservice.repository.CourseRepository;
import soa.courseservice.service.CourseService;

class CourseSecurityAndOwnershipTest {

    private CourseRepository courseRepository;
    private UserClient userClient;
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        userClient = mock(UserClient.class);
        courseService = new CourseService(courseRepository, userClient);
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
    void testCreateCourse_InstructorIdBoundToAuthenticatedUser() {
        authenticateAs("prof_rahul", "INSTRUCTOR");

        CourseRequest request = new CourseRequest();
        request.setTitle("Cloud Computing");
        request.setInstructor("Prof. Rahul");
        // Attacker attempts to forge instructorId to another instructor
        request.setInstructorId("prof_smith");
        request.setCapacity(40);

        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(101L);
            return c;
        });

        CourseResponse response = courseService.createCourse(request);

        assertNotNull(response);
        // Must be bound to prof_rahul, NOT prof_smith!
        assertEquals("prof_rahul", response.getInstructorId());
    }

    @Test
    void testUpdateCourse_OwnCourse_Succeeds() {
        authenticateAs("prof_rahul", "INSTRUCTOR");

        Course course = new Course();
        course.setId(1L);
        course.setTitle("Old Title");
        course.setInstructorId("prof_rahul");
        course.setCapacity(30);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseRequest updateRequest = new CourseRequest();
        updateRequest.setTitle("New Title");
        updateRequest.setCapacity(35);

        CourseResponse response = courseService.updateCourse(1L, updateRequest);

        assertEquals("New Title", response.getTitle());
        assertEquals(35, response.getCapacity());
    }

    @Test
    void testUpdateCourse_AnotherInstructorsCourse_ThrowsAccessDenied() {
        authenticateAs("prof_intruder", "INSTRUCTOR");

        Course course = new Course();
        course.setId(1L);
        course.setTitle("Original Title");
        course.setInstructorId("prof_rahul"); // belongs to prof_rahul
        course.setCapacity(30);

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseRequest updateRequest = new CourseRequest();
        updateRequest.setTitle("Hacked Title");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> courseService.updateCourse(1L, updateRequest));
        assertEquals("Instructors can only modify their own courses", ex.getMessage());
        verify(courseRepository, never()).save(any());
    }

    @Test
    void testDeleteCourse_AnotherInstructorsCourse_ThrowsAccessDenied() {
        authenticateAs("prof_intruder", "INSTRUCTOR");

        Course course = new Course();
        course.setId(1L);
        course.setInstructorId("prof_rahul");

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> courseService.deleteCourse(1L));
        assertEquals("Instructors can only delete their own courses", ex.getMessage());
        verify(courseRepository, never()).delete(any());
    }

    @Test
    void testGetCoursesByInstructor_AnotherInstructor_ThrowsAccessDenied() {
        authenticateAs("prof_intruder", "INSTRUCTOR");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> courseService.getCoursesByInstructor("prof_rahul"));
        assertEquals("Instructors can only view courses they manage", ex.getMessage());
    }

    @Test
    void testUpdateCourse_AdminCanUpdateAnyCourse() {
        authenticateAs("admin_user", "ADMIN");

        Course course = new Course();
        course.setId(2L);
        course.setTitle("Original Title");
        course.setInstructorId("prof_rahul");
        course.setCapacity(20);

        when(courseRepository.findById(2L)).thenReturn(Optional.of(course));
        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        CourseRequest updateRequest = new CourseRequest();
        updateRequest.setTitle("Admin Modified Title");
        updateRequest.setCapacity(50);

        CourseResponse response = courseService.updateCourse(2L, updateRequest);

        assertNotNull(response);
        assertEquals("Admin Modified Title", response.getTitle());
        assertEquals(50, response.getCapacity());
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    void testDeleteCourse_AdminCanDeleteAnyCourse() {
        authenticateAs("admin_user", "ADMIN");

        Course course = new Course();
        course.setId(3L);
        course.setTitle("Course To Delete");
        course.setInstructorId("prof_rahul");

        when(courseRepository.findById(3L)).thenReturn(Optional.of(course));

        courseService.deleteCourse(3L);

        verify(courseRepository).delete(course);
    }

    @Test
    void testCreateCourse_AdminCanAssignInstructor() {
        authenticateAs("admin_user", "ADMIN");

        when(userClient.existsById("prof_assigned")).thenReturn(org.springframework.http.ResponseEntity.ok(true));

        CourseRequest request = new CourseRequest();
        request.setTitle("Advanced Algorithms");
        request.setInstructor("Prof. Assigned");
        request.setInstructorId("prof_assigned");
        request.setCapacity(45);

        when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
            Course c = inv.getArgument(0);
            c.setId(102L);
            return c;
        });

        CourseResponse response = courseService.createCourse(request);

        assertNotNull(response);
        assertEquals("prof_assigned", response.getInstructorId());
        assertEquals("Advanced Algorithms", response.getTitle());
    }
}
