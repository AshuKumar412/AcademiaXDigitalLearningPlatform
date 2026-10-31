package soa.courseservice.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CourseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleCourseNotFound(CourseNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "COURSE_NOT_FOUND");
        error.put("message", ex.getMessage());
        return error;
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, String> handleAccessDenied(AccessDeniedException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "FORBIDDEN");
        error.put("message", ex.getMessage());
        return error;
    }

    @ExceptionHandler(InstructorValidationException.class)
    public ResponseEntity<Map<String, String>> handleInstructorValidation(InstructorValidationException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "INSTRUCTOR_VALIDATION_FAILED");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleUserServiceUnavailable(UserServiceUnavailableException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "USER_SERVICE_UNAVAILABLE");
        error.put("message", ex.getMessage());
        return error;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBadRequest(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "BAD_REQUEST");
        error.put("message", ex.getMessage());
        return error;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(IllegalStateException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "CONFLICT");
        error.put("message", ex.getMessage());
        return error;
    }
}
