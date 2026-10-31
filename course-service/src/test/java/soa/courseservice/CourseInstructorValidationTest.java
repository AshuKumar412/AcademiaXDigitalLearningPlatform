package soa.courseservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import soa.courseservice.client.UserClient;
import soa.courseservice.dto.CourseRequest;
import soa.courseservice.dto.CourseResponse;
import soa.courseservice.dto.UserResponse;
import soa.courseservice.exception.InstructorValidationException;
import soa.courseservice.exception.UserServiceUnavailableException;
import soa.courseservice.model.Course;
import soa.courseservice.repository.CourseRepository;
import soa.courseservice.service.CourseService;

/**
 * Unit tests for instructor validation in CourseService.
 * All tests use mocked UserClient and CourseRepository — NO database writes.
 */
@ExtendWith(MockitoExtension.class)
class CourseInstructorValidationTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private CourseService courseService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- Helper methods ---

    private void setSecurityContext(String username, String role) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + role),
                new SimpleGrantedAuthority(role)
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private CourseRequest buildCourseRequest(String instructorId) {
        CourseRequest req = new CourseRequest();
        req.setTitle("Distributed Systems");
        req.setInstructorId(instructorId);
        req.setInstructor("Untrusted Name From Client");
        req.setCapacity(30);
        req.setStatus("OPEN");
        return req;
    }

    private UserResponse buildUserResponse(String userId, String name, String role) {
        UserResponse user = new UserResponse();
        user.setId(1L);
        user.setUserId(userId);
        user.setName(name);
        user.setRole(role);
        user.setEmail(userId + "@academiax.edu");
        return user;
    }

    private Course savedCourseFrom(Course input) {
        Course saved = new Course();
        saved.setId(1L);
        saved.setTitle(input.getTitle());
        saved.setInstructor(input.getInstructor());
        saved.setInstructorId(input.getInstructorId());
        saved.setCapacity(input.getCapacity());
        saved.setEnrolledCount(0);
        saved.setStatus(input.getStatus());
        saved.setEnrollmentDeadline(input.getEnrollmentDeadline());
        return saved;
    }

    private FeignException.NotFound createFeignNotFoundException() {
        Request request = Request.create(
                Request.HttpMethod.GET, "/users/INVALID_ID",
                Collections.emptyMap(), null, new RequestTemplate());
        return new FeignException.NotFound("Not Found", request, null, null);
    }

    // --- Test 1: Valid instructor → success ---

    @Test
    @DisplayName("1. Valid existing instructor (INSTRUCTOR role) → course created with verified name")
    void testCreateCourse_ValidInstructor_Succeeds() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest("INS001");
        UserResponse instructorUser = buildUserResponse("INS001", "Dr. Jane Smith", "INSTRUCTOR");

        when(userClient.getUserById("INS001")).thenReturn(ResponseEntity.ok(instructorUser));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> savedCourseFrom(invocation.getArgument(0)));

        CourseResponse response = courseService.createCourse(request);

        assertNotNull(response);
        assertEquals("INS001", response.getInstructorId());
        // Verify that the name comes from User Service, NOT from the request body
        assertEquals("Dr. Jane Smith", response.getInstructor());
        assertEquals("Distributed Systems", response.getTitle());
        assertEquals(30, response.getCapacity());

        verify(userClient).getUserById("INS001");
        verify(courseRepository).save(any(Course.class));
    }

    // --- Test 2: Missing instructorId → 400 ---

    @Test
    @DisplayName("2. Missing instructorId (not set in request) → InstructorValidationException 400")
    void testCreateCourse_MissingInstructorId_Returns400() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest(null); // instructorId is null

        InstructorValidationException ex = assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(request));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("instructorId is required when creating a course", ex.getMessage());

        verify(userClient, never()).getUserById(anyString());
        verify(courseRepository, never()).save(any(Course.class));
    }

    // --- Test 3: Blank instructorId → 400 ---

    @Test
    @DisplayName("3. Null or blank instructorId → InstructorValidationException 400")
    void testCreateCourse_BlankInstructorId_Returns400() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest("   "); // blank whitespace

        InstructorValidationException ex = assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(request));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("instructorId is required when creating a course", ex.getMessage());

        verify(userClient, never()).getUserById(anyString());
        verify(courseRepository, never()).save(any(Course.class));
    }

    // --- Test 4: Invalid instructorId (not found in User Service) → 404 ---

    @Test
    @DisplayName("4. Invalid instructorId (not found in User Service) → InstructorValidationException 404")
    void testCreateCourse_InvalidInstructorId_Returns404() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest("NONEXISTENT_USER");

        when(userClient.getUserById("NONEXISTENT_USER")).thenThrow(createFeignNotFoundException());

        InstructorValidationException ex = assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(request));

        assertEquals(404, ex.getHttpStatus());
        assertEquals("Instructor not found with id: NONEXISTENT_USER", ex.getMessage());

        verify(userClient).getUserById("NONEXISTENT_USER");
        verify(courseRepository, never()).save(any(Course.class));
    }

    // --- Test 5: Student ID supplied as instructorId → 400 ---

    @Test
    @DisplayName("5. Student ID supplied as instructorId → InstructorValidationException 400")
    void testCreateCourse_StudentAsInstructor_Returns400() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest("STU101");
        UserResponse studentUser = buildUserResponse("STU101", "John Doe", "STUDENT");

        when(userClient.getUserById("STU101")).thenReturn(ResponseEntity.ok(studentUser));

        InstructorValidationException ex = assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(request));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("User 'STU101' has role STUDENT but only users with INSTRUCTOR role can be assigned to courses",
                ex.getMessage());

        verify(userClient).getUserById("STU101");
        verify(courseRepository, never()).save(any(Course.class));
    }

    // --- Test 6: Admin ID supplied as instructorId → 400 ---

    @Test
    @DisplayName("6. Admin ID supplied as instructorId → InstructorValidationException 400")
    void testCreateCourse_AdminAsInstructor_Returns400() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest("ADMIN001");
        UserResponse adminUser = buildUserResponse("ADMIN001", "Admin User", "ADMIN");

        when(userClient.getUserById("ADMIN001")).thenReturn(ResponseEntity.ok(adminUser));

        InstructorValidationException ex = assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(request));

        assertEquals(400, ex.getHttpStatus());
        assertEquals("User 'ADMIN001' has role ADMIN but only users with INSTRUCTOR role can be assigned to courses",
                ex.getMessage());

        verify(userClient).getUserById("ADMIN001");
        verify(courseRepository, never()).save(any(Course.class));
    }

    // --- Test 7: Unauthenticated request → no SecurityContext set ---
    // (Security layer rejects before reaching service, but we test service-level behavior)

    @Test
    @DisplayName("7. Unauthenticated request (no SecurityContext) → instructorId still validated")
    void testCreateCourse_UnauthenticatedButInstructorIdProvided_StillValidates() {
        // No SecurityContext set → getCurrentUser() returns null, isCurrentUserInstructor() returns false
        CourseRequest request = buildCourseRequest("INS001");
        UserResponse instructorUser = buildUserResponse("INS001", "Dr. Jane Smith", "INSTRUCTOR");

        when(userClient.getUserById("INS001")).thenReturn(ResponseEntity.ok(instructorUser));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> savedCourseFrom(invocation.getArgument(0)));

        // If the request somehow bypasses Spring Security, the service still validates instructor
        CourseResponse response = courseService.createCourse(request);
        assertNotNull(response);
        assertEquals("INS001", response.getInstructorId());
    }

    // --- Test 8: Instructor creates course under own identity ---

    @Test
    @DisplayName("8. INSTRUCTOR role user creates course → instructorId overridden to own identity")
    void testCreateCourse_InstructorCreatesOwnCourse_OverridesInstructorId() {
        setSecurityContext("instructor1", "INSTRUCTOR");

        // Even if request supplies a different instructorId, the service overrides it
        CourseRequest request = buildCourseRequest("SOME_OTHER_INSTRUCTOR");
        UserResponse instructorUser = buildUserResponse("instructor1", "Prof. Alice", "INSTRUCTOR");

        when(userClient.getUserById("instructor1")).thenReturn(ResponseEntity.ok(instructorUser));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> savedCourseFrom(invocation.getArgument(0)));

        CourseResponse response = courseService.createCourse(request);

        assertNotNull(response);
        // Verify instructorId was overridden to the authenticated user
        assertEquals("instructor1", response.getInstructorId());
        assertEquals("Prof. Alice", response.getInstructor());

        // Verify lookup was for authenticated user, not the request body value
        verify(userClient).getUserById("instructor1");
    }

    // --- Test 9: User Service unavailable → 503 ---

    @Test
    @DisplayName("9. User Service unavailable → UserServiceUnavailableException (503)")
    void testCreateCourse_UserServiceUnavailable_Returns503() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest("INS001");

        when(userClient.getUserById("INS001")).thenThrow(new RuntimeException("Connection refused"));

        UserServiceUnavailableException ex = assertThrows(UserServiceUnavailableException.class,
                () -> courseService.createCourse(request));

        assertEquals("User Service is unavailable. Cannot validate instructor. Please try again later.",
                ex.getMessage());

        verify(userClient).getUserById("INS001");
        verify(courseRepository, never()).save(any(Course.class));
    }

    // --- Test 10: Verify no database writes in any failure test ---

    @Test
    @DisplayName("10. Verify no database writes occur during any validation failure path")
    void testNoDatabaseWritesOnValidationFailure() {
        setSecurityContext("admin1", "ADMIN");

        // Scenario A: null instructorId
        assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(buildCourseRequest(null)));

        // Scenario B: blank instructorId
        assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(buildCourseRequest("")));

        // Scenario C: not found
        when(userClient.getUserById("MISSING")).thenThrow(createFeignNotFoundException());
        assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(buildCourseRequest("MISSING")));

        // Scenario D: wrong role
        UserResponse studentUser = buildUserResponse("STU999", "Student", "STUDENT");
        when(userClient.getUserById("STU999")).thenReturn(ResponseEntity.ok(studentUser));
        assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(buildCourseRequest("STU999")));

        // Verify: no save() calls at all across all failure scenarios
        verify(courseRepository, never()).save(any(Course.class));
    }

    // --- Test: Instructor name from request body is NOT trusted ---

    @Test
    @DisplayName("Instructor name from request body is ignored — verified name from User Service is stored")
    void testCreateCourse_InstructorNameFromUserService_NotRequestBody() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest("INS001");
        request.setInstructor("FAKE NAME FROM ATTACKER");

        UserResponse instructor = buildUserResponse("INS001", "Real Prof. Name", "INSTRUCTOR");
        when(userClient.getUserById("INS001")).thenReturn(ResponseEntity.ok(instructor));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> savedCourseFrom(invocation.getArgument(0)));

        CourseResponse response = courseService.createCourse(request);

        assertEquals("Real Prof. Name", response.getInstructor());
        assertEquals("INS001", response.getInstructorId());
    }

    // --- Test: User Service returns null body → 404 ---

    @Test
    @DisplayName("User Service returns null body → InstructorValidationException 404")
    void testCreateCourse_UserServiceReturnsNullBody_Returns404() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest("INS001");
        when(userClient.getUserById("INS001")).thenReturn(ResponseEntity.ok(null));

        InstructorValidationException ex = assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(request));

        assertEquals(404, ex.getHttpStatus());
        verify(courseRepository, never()).save(any(Course.class));
    }

    // --- Test: User with null role → 400 ---

    @Test
    @DisplayName("User Service returns user with null role → InstructorValidationException 400")
    void testCreateCourse_UserWithNullRole_Returns400() {
        setSecurityContext("admin1", "ADMIN");

        CourseRequest request = buildCourseRequest("INS001");
        UserResponse userNoRole = buildUserResponse("INS001", "No Role User", null);
        when(userClient.getUserById("INS001")).thenReturn(ResponseEntity.ok(userNoRole));

        InstructorValidationException ex = assertThrows(InstructorValidationException.class,
                () -> courseService.createCourse(request));

        assertEquals(400, ex.getHttpStatus());
        verify(courseRepository, never()).save(any(Course.class));
    }
}
