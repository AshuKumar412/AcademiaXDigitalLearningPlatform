package soa.enrollmentservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import soa.enrollmentservice.dto.CourseClientResponse;

@FeignClient(name = "course-service")
public interface CourseClient {

    @GetMapping("/courses/{id}")
    CourseClientResponse getCourseById(@PathVariable("id") Long id);
}
