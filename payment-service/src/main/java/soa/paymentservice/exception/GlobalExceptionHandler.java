package soa.paymentservice.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(PaymentNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "PAYMENT_NOT_FOUND");
        error.put("message", ex.getMessage());
        return error;
    }

    // 404 — referenced enrollment does not exist
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleBadRef(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "ENROLLMENT_NOT_FOUND");
        error.put("message", ex.getMessage());
        return error;
    }

    // 409 — duplicate payment for same enrollment
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(IllegalStateException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "DUPLICATE_PAYMENT");
        error.put("message", ex.getMessage());
        return error;
    }

    // 503 — downstream service unavailable
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> handleRuntime(RuntimeException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "SERVICE_ERROR");
        error.put("message", ex.getMessage());
        return error;
    }
}