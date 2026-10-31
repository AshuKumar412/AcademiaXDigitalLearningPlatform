package soa.courseservice.exception;

/**
 * Thrown when the User Service is unreachable during instructor validation.
 */
public class UserServiceUnavailableException extends RuntimeException {

    public UserServiceUnavailableException(String message) {
        super(message);
    }

    public UserServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
