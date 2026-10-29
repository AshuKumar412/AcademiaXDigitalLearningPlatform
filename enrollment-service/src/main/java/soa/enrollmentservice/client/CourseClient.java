package soa.enrollmentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import soa.enrollmentservice.dto.CourseClientResponse;

@FeignClient(name = "course-service")
public interface CourseClient {

    @GetMapping("/courses/{id}")
    CourseClientResponse getCourseById(@PathVariable("id") Long id);

    @PostMapping("/courses/{id}/reserve-seat")
    ResponseEntity<Boolean> reserveSeat(@PathVariable("id") Long id);

    @PostMapping("/courses/{id}/release-seat")
    ResponseEntity<Boolean> releaseSeat(@PathVariable("id") Long id);
}
