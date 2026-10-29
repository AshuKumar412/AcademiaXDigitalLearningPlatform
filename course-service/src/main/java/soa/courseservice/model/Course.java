package soa.courseservice.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String instructor;   // instructor name (display)
    private String instructorId; // instructor userId from User Service
    private int capacity;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount = 0;

    @Column(name = "status", nullable = false)
    private String status = "OPEN"; // OPEN or CLOSED

    @Column(name = "enrollment_deadline")
    private LocalDateTime enrollmentDeadline;

    public Long getId() { 
        return id; 
    }
    public void setId(Long id) { 
        this.id = id; 
    }

    public String getTitle() { 
        return title; 
    }
    public void setTitle(String title) { 
        this.title = title; 
    }

    public String getInstructor() { 
        return instructor; 
    }
    public void setInstructor(String instructor) { 
        this.instructor = instructor; 
    }

    public String getInstructorId() { 
        return instructorId; 
    }
    public void setInstructorId(String instructorId) { 
        this.instructorId = instructorId; 
    }

    public int getCapacity() { 
        return capacity; 
    }
    public void setCapacity(int capacity) { 
        this.capacity = capacity; 
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }
    public void setEnrolledCount(int enrolledCount) {
        this.enrolledCount = enrolledCount;
    }

    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getEnrollmentDeadline() {
        return enrollmentDeadline;
    }
    public void setEnrollmentDeadline(LocalDateTime enrollmentDeadline) {
        this.enrollmentDeadline = enrollmentDeadline;
    }
}