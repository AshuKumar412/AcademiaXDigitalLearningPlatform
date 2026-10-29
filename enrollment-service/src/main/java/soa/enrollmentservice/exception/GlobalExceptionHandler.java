package soa.enrollmentservice.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EnrollmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(EnrollmentNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "ENROLLMENT_NOT_FOUND");
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

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleBadRef(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "REFERENCE_NOT_FOUND");
        error.put("message", ex.getMessage());
        return error;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(IllegalStateException ex) {
        Map<String, String> error = new HashMap<>();
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("Payment failed") || msg.contains("Payment rejected") || msg.contains("rejected by provider")) {
            error.put("error", "PAYMENT_FAILED");
        } else if (msg.contains("capacity") || msg.contains("closed") || msg.contains("deadline")) {
            error.put("error", "COURSE_UNAVAILABLE");
        } else if (msg.contains("already enrolled")) {
            error.put("error", "ALREADY_ENROLLED");
        } else {
            error.put("error", "CONFLICT");
        }
        error.put("message", ex.getMessage());
        return error;
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleRuntime(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "SERVICE_ERROR");
        error.put("message", ex.getMessage());
        return error;
    }
}