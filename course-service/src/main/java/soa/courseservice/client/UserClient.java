package soa.courseservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import soa.courseservice.dto.UserResponse;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/users/{userId}/exists")
    ResponseEntity<Boolean> existsById(@PathVariable("userId") String userId);

    @GetMapping("/users/{userId}")
    ResponseEntity<UserResponse> getUserById(@PathVariable("userId") String userId);
}
