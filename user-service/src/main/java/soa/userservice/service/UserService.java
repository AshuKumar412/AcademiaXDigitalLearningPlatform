package soa.userservice.service;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import soa.userservice.dto.UserRequest;
import soa.userservice.dto.UserResponse;
import soa.userservice.dto.UserUpdateRequest;
import soa.userservice.exception.UserNotFoundException;
import soa.userservice.model.User;
import soa.userservice.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // CREATE
    public UserResponse createUser(UserRequest request) {

        if (request.getRole() == null || request.getRole().isBlank()) {
            throw new IllegalArgumentException("role is required (STUDENT or INSTRUCTOR)");
        }

        String userId;
        if ("STUDENT".equalsIgnoreCase(request.getRole())) {
            if (request.getStudentId() == null || request.getStudentId().isBlank()) {
                throw new IllegalArgumentException("studentId is required for role STUDENT");
            }
            userId = request.getStudentId();
        } else if ("INSTRUCTOR".equalsIgnoreCase(request.getRole())) {
            if (request.getInstructorId() == null || request.getInstructorId().isBlank()) {
                throw new IllegalArgumentException("instructorId is required for role INSTRUCTOR");
            }
            userId = request.getInstructorId();
        } else {
            throw new IllegalArgumentException("role must be STUDENT or INSTRUCTOR");
        }

        // Duplicate check
        if (userRepository.existsById(userId)) {
            throw new IllegalStateException("User with id '" + userId + "' already exists");
        }

        User user = new User();
        user.setUserId(userId);
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole().toUpperCase());
        user.setDepartment(request.getDepartment());

        if ("STUDENT".equalsIgnoreCase(request.getRole())) {
            user.setStudentId(request.getStudentId());
            user.setInstructorId(null);
        } else {
            user.setInstructorId(request.getInstructorId());
            user.setStudentId(null);
        }

        User savedUser = userRepository.save(user);
        return convertToResponse(savedUser);
    }

    // READ ALL (Admin only via SecurityConfig)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToResponse)
                .toList();
    }

    // READ BY userId
    public UserResponse getUserById(String userId) {
        checkUserOwnership(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        return convertToResponse(user);
    }

    // CHECK EXISTS (used by other services via endpoint)
    public boolean existsById(String userId) {
        return userRepository.existsById(userId);
    }

    // UPDATE
    public UserResponse updateUser(String userId, UserUpdateRequest request) {
        checkUserOwnership(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setDepartment(request.getDepartment());

        if ("STUDENT".equalsIgnoreCase(user.getRole()) && request.getStudentId() != null && !request.getStudentId().isBlank()) {
            user.setStudentId(request.getStudentId());
        } else if ("INSTRUCTOR".equalsIgnoreCase(user.getRole()) && request.getInstructorId() != null && !request.getInstructorId().isBlank()) {
            user.setInstructorId(request.getInstructorId());
        }

        return convertToResponse(userRepository.save(user));
    }

    // DELETE
    public void deleteUser(String userId) {
        checkUserOwnership(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        userRepository.delete(user);
    }

    private void checkUserOwnership(String userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) {
                String currentUser = auth.getName();
                if (currentUser != null && !currentUser.equalsIgnoreCase(userId)) {
                    throw new AccessDeniedException("Users can only access their own profile");
                }
            }
        }
    }

    private UserResponse convertToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRole(user.getRole());
        response.setDepartment(user.getDepartment());
        response.setStudentId(user.getStudentId());
        response.setInstructorId(user.getInstructorId());
        return response;
    }
}