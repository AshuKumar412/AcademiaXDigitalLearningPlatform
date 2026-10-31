package soa.courseservice.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import feign.FeignException;
import soa.courseservice.client.UserClient;
import soa.courseservice.dto.CourseRequest;
import soa.courseservice.dto.CourseResponse;
import soa.courseservice.dto.UserResponse;
import soa.courseservice.exception.CourseNotFoundException;
import soa.courseservice.exception.InstructorValidationException;
import soa.courseservice.exception.UserServiceUnavailableException;
import soa.courseservice.model.Course;
import soa.courseservice.repository.CourseRepository;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final UserClient userClient;

    public CourseService(CourseRepository courseRepository, UserClient userClient) {
        this.courseRepository = courseRepository;
        this.userClient = userClient;
    }

    // CREATE - validates instructor exists via User Service and has INSTRUCTOR role
    public CourseResponse createCourse(CourseRequest request) {

        String instructorId = request.getInstructorId();

        // If the current user is an INSTRUCTOR, force instructorId to their own identity
        if (isCurrentUserInstructor()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && !currentUser.isBlank()) {
                instructorId = currentUser;
            }
        }

        // Validate instructorId is present
        if (instructorId == null || instructorId.isBlank()) {
            throw new InstructorValidationException(
                    "instructorId is required when creating a course", 400);
        }

        // Validate instructor via User Service
        UserResponse instructor = fetchAndValidateInstructor(instructorId);

        Course course = new Course();
        course.setTitle(request.getTitle());
        // Use the verified name from User Service, not the untrusted request body
        course.setInstructor(instructor.getName());
        course.setInstructorId(instructorId);
        course.setCapacity(request.getCapacity());
        course.setStatus(request.getStatus() != null ? request.getStatus().toUpperCase() : "OPEN");
        course.setEnrollmentDeadline(request.getEnrollmentDeadline());
        course.setEnrolledCount(0);

        Course savedCourse = courseRepository.save(course);
        return convertToResponse(savedCourse);
    }

    /**
     * Fetches user details from User Service via Feign and validates:
     * 1. The user exists (404 if not)
     * 2. The user has the INSTRUCTOR role (400 if STUDENT or ADMIN)
     *
     * @param instructorId the userId to look up in User Service
     * @return the validated UserResponse with instructor details
     * @throws InstructorValidationException if the user doesn't exist or is not an INSTRUCTOR
     * @throws UserServiceUnavailableException if User Service cannot be reached
     */
    UserResponse fetchAndValidateInstructor(String instructorId) {
        ResponseEntity<UserResponse> response;
        try {
            response = userClient.getUserById(instructorId);
        } catch (FeignException.NotFound e) {
            throw new InstructorValidationException(
                    "Instructor not found with id: " + instructorId, 404);
        } catch (FeignException e) {
            throw new UserServiceUnavailableException(
                    "User Service is unavailable. Cannot validate instructor. Please try again later.", e);
        } catch (Exception e) {
            throw new UserServiceUnavailableException(
                    "User Service is unavailable. Cannot validate instructor. Please try again later.", e);
        }

        if (response == null || response.getBody() == null) {
            throw new InstructorValidationException(
                    "Instructor not found with id: " + instructorId, 404);
        }

        UserResponse user = response.getBody();

        // Validate the user has the INSTRUCTOR role
        String role = user.getRole();
        if (role == null || !role.trim().equalsIgnoreCase("INSTRUCTOR")) {
            String actualRole = (role != null) ? role.trim().toUpperCase() : "UNKNOWN";
            throw new InstructorValidationException(
                    "User '" + instructorId + "' has role " + actualRole
                            + " but only users with INSTRUCTOR role can be assigned to courses", 400);
        }

        return user;
    }

    // READ ALL
    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
    }

    // READ BY ID
    public CourseResponse getCourseById(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Course not found with id: " + id));
        return convertToResponse(course);
    }

    // READ BY INSTRUCTOR ID
    public List<CourseResponse> getCoursesByInstructor(String instructorId) {
        // If caller is an instructor, ensure they can only query their own courses
        if (isCurrentUserInstructor()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && !currentUser.equalsIgnoreCase(instructorId)) {
                throw new AccessDeniedException("Instructors can only view courses they manage");
            }
        }

        return courseRepository.findByInstructorId(instructorId).stream()
                .map(this::convertToResponse)
                .toList();
    }

    // ATOMIC SEAT RESERVATION (Concurrency-safe)
    @Transactional
    public boolean reserveSeat(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Course not found with id: " + id));

        if ("CLOSED".equalsIgnoreCase(course.getStatus())) {
            throw new IllegalStateException("Course registration is closed for course " + id);
        }

        if (course.getEnrollmentDeadline() != null && course.getEnrollmentDeadline().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Course enrollment deadline has passed for course " + id);
        }

        int updatedRows = courseRepository.atomicReserveSeat(id);
        if (updatedRows == 0) {
            throw new IllegalStateException("Course capacity reached. Maximum capacity is " + course.getCapacity());
        }

        return true;
    }

    // ATOMIC SEAT RELEASE (Compensation / Rollback)
    @Transactional
    public boolean releaseSeat(Long id) {
        courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Course not found with id: " + id));

        courseRepository.atomicReleaseSeat(id);
        return true;
    }

    // UPDATE - enforces instructor ownership (ADMIN can update any course)
    public CourseResponse updateCourse(Long id, CourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Course not found with id: " + id));

        // Enforce ownership: Instructor can only update their own courses
        if (!isCurrentUserAdmin() && isCurrentUserInstructor()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && course.getInstructorId() != null && !course.getInstructorId().equalsIgnoreCase(currentUser)) {
                boolean isOwn = false;
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getDetails() instanceof io.jsonwebtoken.Claims claims) {
                    String claimUserId = claims.get("userId", String.class);
                    Object claimId = claims.get("id");
                    if (claimUserId != null && course.getInstructorId().equalsIgnoreCase(claimUserId)) {
                        isOwn = true;
                    } else if (claimId != null && course.getInstructorId().equalsIgnoreCase(String.valueOf(claimId))) {
                        isOwn = true;
                    }
                }
                if (!isOwn) {
                    throw new AccessDeniedException("Instructors can only modify their own courses");
                }
            }
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            course.setTitle(request.getTitle());
        }
        if (request.getInstructor() != null && !request.getInstructor().isBlank()) {
            course.setInstructor(request.getInstructor());
        }
        if (request.getInstructorId() != null && !request.getInstructorId().isBlank()) {
            course.setInstructorId(request.getInstructorId());
        }
        if (request.getCapacity() > 0) {
            course.setCapacity(request.getCapacity());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            course.setStatus(request.getStatus().toUpperCase());
        }
        if (request.getEnrollmentDeadline() != null) {
            course.setEnrollmentDeadline(request.getEnrollmentDeadline());
        }

        return convertToResponse(courseRepository.save(course));
    }

    // DELETE - enforces instructor ownership (ADMIN can delete any course)
    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Course not found with id: " + id));

        // Enforce ownership: Instructor can only delete their own courses
        if (!isCurrentUserAdmin() && isCurrentUserInstructor()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && course.getInstructorId() != null && !course.getInstructorId().equalsIgnoreCase(currentUser)) {
                boolean isOwn = false;
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getDetails() instanceof io.jsonwebtoken.Claims claims) {
                    String claimUserId = claims.get("userId", String.class);
                    Object claimId = claims.get("id");
                    if (claimUserId != null && course.getInstructorId().equalsIgnoreCase(claimUserId)) {
                        isOwn = true;
                    } else if (claimId != null && course.getInstructorId().equalsIgnoreCase(String.valueOf(claimId))) {
                        isOwn = true;
                    }
                }
                if (!isOwn) {
                    throw new AccessDeniedException("Instructors can only delete their own courses");
                }
            }
        }

        courseRepository.delete(course);
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    public boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            boolean hasAdminAuthority = auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
            if (hasAdminAuthority) {
                return true;
            }
            if (auth.getDetails() instanceof io.jsonwebtoken.Claims claims) {
                Object roleClaim = claims.get("role");
                if ("ADMIN".equalsIgnoreCase(String.valueOf(roleClaim)) || "ROLE_ADMIN".equalsIgnoreCase(String.valueOf(roleClaim))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCurrentUserInstructor() {
        if (isCurrentUserAdmin()) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_INSTRUCTOR") || a.getAuthority().equals("INSTRUCTOR"));
        }
        return false;
    }

    private CourseResponse convertToResponse(Course course) {
        CourseResponse response = new CourseResponse();
        response.setId(course.getId());
        response.setTitle(course.getTitle());
        response.setInstructor(course.getInstructor());
        response.setInstructorId(course.getInstructorId());
        response.setCapacity(course.getCapacity());
        response.setEnrolledCount(course.getEnrolledCount());
        response.setAvailableSeats(Math.max(0, course.getCapacity() - course.getEnrolledCount()));
        response.setStatus(course.getStatus() != null ? course.getStatus() : "OPEN");
        response.setEnrollmentDeadline(course.getEnrollmentDeadline());
        return response;
    }
}
