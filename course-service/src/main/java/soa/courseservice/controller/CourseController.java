package soa.courseservice.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import soa.courseservice.dto.CourseRequest;
import soa.courseservice.dto.CourseResponse;
import soa.courseservice.service.CourseService;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    // CREATE
    @PostMapping
    public CourseResponse createCourse(@RequestBody CourseRequest request) {
        return courseService.createCourse(request);
    }

    // READ ALL
    @GetMapping
    public List<CourseResponse> getAllCourses() {
        return courseService.getAllCourses();
    }

    // READ BY ID
    @GetMapping("/{id}")
    public CourseResponse getCourseById(@PathVariable Long id) {
        return courseService.getCourseById(id);
    }

    // READ BY INSTRUCTOR ID
    @GetMapping("/by-instructor/{instructorId}")
    public List<CourseResponse> getCoursesByInstructor(@PathVariable String instructorId) {
        return courseService.getCoursesByInstructor(instructorId);
    }

    // ATOMIC SEAT RESERVATION
    @PostMapping("/{id}/reserve-seat")
    public ResponseEntity<Boolean> reserveSeat(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.reserveSeat(id));
    }

    // ATOMIC SEAT RELEASE (Saga Compensation)
    @PostMapping("/{id}/release-seat")
    public ResponseEntity<Boolean> releaseSeat(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.releaseSeat(id));
    }

    // UPDATE
    @PutMapping("/{id}")
    public CourseResponse updateCourse(
            @PathVariable Long id,
            @RequestBody CourseRequest request) {

        return courseService.updateCourse(id, request);
    }

    // DELETE
    @DeleteMapping("/{id}")
    public String deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return "Course deleted successfully";
    }
}