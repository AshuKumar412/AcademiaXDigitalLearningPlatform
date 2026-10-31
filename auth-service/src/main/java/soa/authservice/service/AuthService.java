package soa.authservice.service;

import java.util.Optional;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import soa.authservice.dto.LoginRequest;
import soa.authservice.dto.LoginResponse;
import soa.authservice.dto.RegisterRequest;
import soa.authservice.dto.RegisterResponse;
import soa.authservice.exception.UserAlreadyExistsException;
import soa.authservice.model.AuthUser;
import soa.authservice.repository.AuthUserRepository;
import soa.authservice.security.JwtService;

@Service
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AuthUserRepository authUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Registration request cannot be null");
        }

        if (request.getUsername() == null || request.getUsername().trim().length() < 3) {
            throw new IllegalArgumentException("Username is required and must be at least 3 characters");
        }

        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password is required and must be at least 6 characters");
        }

        String normalizedUsername = request.getUsername().trim().toLowerCase();

        if (authUserRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UserAlreadyExistsException("Username '" + request.getUsername().trim() + "' already exists");
        }

        String normalizedEmail = null;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            normalizedEmail = request.getEmail().trim().toLowerCase();
            if (authUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                throw new UserAlreadyExistsException("Email '" + request.getEmail().trim() + "' already exists");
            }
        }

        // Public registration must create STUDENT accounts only
        String role = "STUDENT";
        if (request.getRole() != null && !request.getRole().isBlank()) {
            String requestedRole = request.getRole().trim().toUpperCase();
            if (!requestedRole.equals("STUDENT")) {
                if (requestedRole.equals("INSTRUCTOR") || requestedRole.equals("ADMIN")) {
                    throw new IllegalArgumentException("Public registration is restricted to STUDENT accounts only. INSTRUCTOR and ADMIN accounts must be created through secure authorized operations.");
                } else {
                    throw new IllegalArgumentException("Role must be STUDENT, INSTRUCTOR, or ADMIN");
                }
            }
        }

        AuthUser user = new AuthUser();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);

        AuthUser savedUser = authUserRepository.save(user);

        RegisterResponse response = new RegisterResponse();
        response.setSuccess(true);
        response.setMessage("Registration successful. Please log in to continue.");
        response.setUserId(savedUser.getUsername());
        response.setUsername(savedUser.getUsername());
        response.setRole(savedUser.getRole());

        return response;
    }

    public LoginResponse login(LoginRequest request) {

        if (request == null ||
            request.getUsername() == null || request.getUsername().isBlank() ||
            request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Username and password are required");
        }

        String identifier = request.getUsername().trim().toLowerCase();

        Optional<AuthUser> userOpt = authUserRepository.findByUsernameIgnoreCase(identifier);
        if (userOpt.isEmpty()) {
            userOpt = authUserRepository.findByEmailIgnoreCase(identifier);
        }

        AuthUser user = userOpt.orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtService.generateToken(
                user.getUsername(),
                user.getRole()
        );

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());

        return response;
    }
}