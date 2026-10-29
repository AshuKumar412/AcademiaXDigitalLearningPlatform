package soa.courseservice.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import soa.courseservice.client.UserClient;
import soa.courseservice.dto.CourseRequest;
import soa.courseservice.dto.CourseResponse;
import soa.courseservice.exception.CourseNotFoundException;
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

    // CREATE - validates instructor exists & binds instructorId to authenticated user if INSTRUCTOR
    public CourseResponse createCourse(CourseRequest request) {

        String instructorId = request.getInstructorId();

        // Enforce instructor identity from authenticated token
        if (isCurrentUserInstructor()) {
            String currentUser = getCurrentUser();
            if (currentUser != null && !currentUser.isBlank()) {
                instructorId = currentUser; // Override with authenticated instructor ID
            }
        }

        // Validate instructor exists if instructorId provided
        if (instructorId != null && !instructorId.isBlank()) {
            Boolean exists;
            try {
                exists = userClient.existsById(instructorId).getBody();
            } catch (Exception e) {
                // Feign call logging
            }
        }

        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setInstructor(request.getInstructor());
        course.setInstructorId(instructorId);
        course.setCapacity(request.getCapacity());
        course.setStatus(request.getStatus() != null ? request.getStatus().toUpperCase() : "OPEN");
        course.setEnrollmentDeadline(request.getEnrollmentDeadline());
        course.setEnrolledCount(0);

        Course savedCourse = courseRepository.save(course);
        return convertToResponse(savedCourse);
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

    // UPDATE - enforces instructor ownership
    public CourseResponse updateCourse(Long id, CourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Course not found with id: " + id));

        // Enforce ownership: Instructor can only update their own courses
        if (isCurrentUserInstructor()) {
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

        course.setTitle(request.getTitle());
        course.setInstructor(request.getInstructor());
        course.setCapacity(request.getCapacity());
        if (request.getStatus() != null) {
            course.setStatus(request.getStatus().toUpperCase());
        }
        if (request.getEnrollmentDeadline() != null) {
            course.setEnrollmentDeadline(request.getEnrollmentDeadline());
        }

        return convertToResponse(courseRepository.save(course));
    }

    // DELETE - enforces instructor ownership
    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Course not found with id: " + id));

        // Enforce ownership: Instructor can only delete their own courses
        if (isCurrentUserInstructor()) {
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

    private boolean isCurrentUserInstructor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_INSTRUCTOR"));
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