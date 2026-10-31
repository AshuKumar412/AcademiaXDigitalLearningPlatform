package soa.courseservice.exception;

/**
 * Thrown when instructor validation fails due to missing instructor,
 * wrong role, or invalid instructorId input.
 */
public class InstructorValidationException extends RuntimeException {

    private final int httpStatus;

    public InstructorValidationException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
