package soa.courseservice.service;

import java.util.List;

import org.springframework.stereotype.Service;

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

    // CREATE - validates instructor exists via User Service
    public CourseResponse createCourse(CourseRequest request) {

        // Validate instructor exists if instructorId provided
        if (request.getInstructorId() != null && !request.getInstructorId().isBlank()) {
            Boolean exists;
            try {
                exists = userClient.existsById(request.getInstructorId()).getBody();
            } catch (Exception e) {
                throw new RuntimeException("User Service unavailable. Cannot validate instructor: " + e.getMessage());
            }
            if (exists == null || !exists) {
                throw new IllegalArgumentException("Instructor '" + request.getInstructorId() + "' does not exist. Please create the instructor first.");
            }
        }

        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setInstructor(request.getInstructor());
        course.setInstructorId(request.getInstructorId());
        course.setCapacity(request.getCapacity());

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

    // UPDATE - validates instructor exists
    public CourseResponse updateCourse(Long id, CourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Course not found with id: " + id));

        if (request.getInstructorId() != null && !request.getInstructorId().isBlank()) {
            Boolean exists;
            try {
                exists = userClient.existsById(request.getInstructorId()).getBody();
            } catch (Exception e) {
                throw new RuntimeException("User Service unavailable: " + e.getMessage());
            }
            if (exists == null || !exists) {
                throw new IllegalArgumentException("Instructor '" + request.getInstructorId() + "' does not exist.");
            }
        }

        course.setTitle(request.getTitle());
        course.setInstructor(request.getInstructor());
        course.setInstructorId(request.getInstructorId());
        course.setCapacity(request.getCapacity());

        return convertToResponse(courseRepository.save(course));
    }

    // DELETE
    public void deleteCourse(Long id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new CourseNotFoundException("Course not found with id: " + id));
        courseRepository.delete(course);
    }

    private CourseResponse convertToResponse(Course course) {
        CourseResponse response = new CourseResponse();
        response.setId(course.getId());
        response.setTitle(course.getTitle());
        response.setInstructor(course.getInstructor());
        response.setInstructorId(course.getInstructorId());
        response.setCapacity(course.getCapacity());
        return response;
    }
}