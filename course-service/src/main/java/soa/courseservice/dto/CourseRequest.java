package soa.courseservice.dto;

import java.time.LocalDateTime;

public class CourseRequest {

    private String title;
    private String instructor;   // display name
    private String instructorId; // userId of instructor (validated against User Service)
    private int capacity;
    private String status = "OPEN";
    private LocalDateTime enrollmentDeadline;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getInstructor() { return instructor; }
    public void setInstructor(String instructor) { this.instructor = instructor; }

    public String getInstructorId() { return instructorId; }
    public void setInstructorId(String instructorId) { this.instructorId = instructorId; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getEnrollmentDeadline() { return enrollmentDeadline; }
    public void setEnrollmentDeadline(LocalDateTime enrollmentDeadline) { this.enrollmentDeadline = enrollmentDeadline; }
}