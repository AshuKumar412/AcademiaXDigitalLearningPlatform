package soa.courseservice.model;

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
    Long id;

    String title;
    String instructor;   // instructor name (display)
    String instructorId; // instructor userId from User Service
    int capacity;

    public Long getId() { 
    	return id; 
    	}
    public void setId(Long id) { this.id = id; }

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
}