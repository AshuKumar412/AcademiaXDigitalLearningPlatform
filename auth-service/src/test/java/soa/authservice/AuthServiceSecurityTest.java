package soa.authservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import soa.authservice.dto.LoginRequest;
import soa.authservice.dto.LoginResponse;
import soa.authservice.dto.RegisterRequest;
import soa.authservice.dto.RegisterResponse;
import soa.authservice.exception.UserAlreadyExistsException;
import soa.authservice.model.AuthUser;
import soa.authservice.repository.AuthUserRepository;
import soa.authservice.security.JwtService;
import soa.authservice.service.AuthService;

class AuthServiceSecurityTest {

    private AuthUserRepository authUserRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        authUserRepository = mock(AuthUserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = mock(JwtService.class);
        authService = new AuthService(authUserRepository, passwordEncoder, jwtService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 1. Registration succeeds and returns NO JWT
    @Test
    void testRegister_Succeeds_ReturnsNoJwt() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("StudentUser101");
        request.setEmail("student101@test.com");
        request.setPassword("securePassword123");
        request.setRole("STUDENT");

        when(authUserRepository.existsByUsernameIgnoreCase("studentuser101")).thenReturn(false);
        when(authUserRepository.existsByEmailIgnoreCase("student101@test.com")).thenReturn(false);

        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(inv -> {
            AuthUser saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        RegisterResponse response = authService.register(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("Registration successful. Please log in to continue.", response.getMessage());
        assertEquals("studentuser101", response.getUserId());
        assertEquals("studentuser101", response.getUsername());
        assertEquals("STUDENT", response.getRole());

        // Verify that JWT token is NEVER generated or returned during registration
        verify(jwtService, never()).generateToken(any(), any());
    }

    // 2. Registration does not automatically authenticate the user
    @Test
    void testRegister_DoesNotAuthenticateUser() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("AliceStudent");
        request.setPassword("password123");
        request.setRole("STUDENT");

        when(authUserRepository.existsByUsernameIgnoreCase("alicestudent")).thenReturn(false);
        when(authUserRepository.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        // Security context must remain unauthenticated
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // 3. Duplicate registration is rejected
    @Test
    void testRegister_DuplicateUsername_ThrowsUserAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("student1");
        request.setPassword("password123");

        when(authUserRepository.existsByUsernameIgnoreCase("student1")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(authUserRepository, never()).save(any());
    }

    @Test
    void testRegister_DuplicateEmail_ThrowsUserAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newstudent");
        request.setEmail("existing@test.com");
        request.setPassword("password123");

        when(authUserRepository.existsByUsernameIgnoreCase("newstudent")).thenReturn(false);
        when(authUserRepository.existsByEmailIgnoreCase("existing@test.com")).thenReturn(true);

        UserAlreadyExistsException ex = assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(authUserRepository, never()).save(any());
    }

    // 4. Invalid input rejection during registration
    @Test
    void testRegister_InvalidInput_ThrowsBadRequest() {
        // Short username
        RegisterRequest shortUser = new RegisterRequest();
        shortUser.setUsername("ab");
        shortUser.setPassword("password123");
        assertThrows(IllegalArgumentException.class, () -> authService.register(shortUser));

        // Short password
        RegisterRequest shortPass = new RegisterRequest();
        shortPass.setUsername("validuser");
        shortPass.setPassword("123");
        assertThrows(IllegalArgumentException.class, () -> authService.register(shortPass));

        // Invalid role
        RegisterRequest invalidRole = new RegisterRequest();
        invalidRole.setUsername("validuser");
        invalidRole.setPassword("password123");
        invalidRole.setRole("SUPERUSER");
        assertThrows(IllegalArgumentException.class, () -> authService.register(invalidRole));
    }

    // 5. Successful login returns valid signed JWT with single token field
    @Test
    void testLogin_RegisteredUser_WithCorrectPassword_ReturnsSignedJwt() {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("student1");
        user.setPassword(passwordEncoder.encode("correctPassword"));
        user.setRole("STUDENT");

        when(authUserRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("student1", "STUDENT")).thenReturn("mock-signed-jwt-token");

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("STUDENT1"); // Case-insensitive login
        loginRequest.setPassword("correctPassword");

        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("mock-signed-jwt-token", response.getToken());
        assertEquals("student1", response.getUsername());
        assertEquals("STUDENT", response.getRole());
    }

    // 6. Login using email succeeds
    @Test
    void testLogin_WithEmailIdentifier_Succeeds() {
        AuthUser user = new AuthUser();
        user.setId(2L);
        user.setUsername("instructor1");
        user.setEmail("instructor1@academia.edu");
        user.setPassword(passwordEncoder.encode("profPassword123"));
        user.setRole("INSTRUCTOR");

        when(authUserRepository.findByUsernameIgnoreCase("instructor1@academia.edu")).thenReturn(Optional.empty());
        when(authUserRepository.findByEmailIgnoreCase("instructor1@academia.edu")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("instructor1", "INSTRUCTOR")).thenReturn("instructor-jwt");

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("instructor1@academia.edu");
        loginRequest.setPassword("profPassword123");

        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("instructor-jwt", response.getToken());
        assertEquals("instructor1", response.getUsername());
        assertEquals("INSTRUCTOR", response.getRole());
    }

    // 7. Unregistered user cannot log in
    @Test
    void testLogin_UnregisteredUser_ThrowsBadCredentials() {
        when(authUserRepository.findByUsernameIgnoreCase("unknownuser")).thenReturn(Optional.empty());
        when(authUserRepository.findByEmailIgnoreCase("unknownuser")).thenReturn(Optional.empty());

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("unknownuser");
        loginRequest.setPassword("anyPassword");

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
        assertEquals("Invalid username or password", ex.getMessage());
        verify(jwtService, never()).generateToken(any(), any());
    }

    // 8. Incorrect password does not produce a token
    @Test
    void testLogin_IncorrectPassword_ThrowsBadCredentials() {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("student1");
        user.setPassword(passwordEncoder.encode("correctPassword"));
        user.setRole("STUDENT");

        when(authUserRepository.findByUsernameIgnoreCase("student1")).thenReturn(Optional.of(user));

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("student1");
        loginRequest.setPassword("wrongPassword");

        BadCredentialsException ex = assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest));
        assertEquals("Invalid username or password", ex.getMessage());
        verify(jwtService, never()).generateToken(any(), any());
    }

    // 9. Invalid or missing login fields are rejected
    @Test
    void testLogin_MissingOrInvalidFields_ThrowsBadRequest() {
        LoginRequest nullUser = new LoginRequest();
        nullUser.setUsername(null);
        nullUser.setPassword("password123");
        assertThrows(IllegalArgumentException.class, () -> authService.login(nullUser));

        LoginRequest blankUser = new LoginRequest();
        blankUser.setUsername("   ");
        blankUser.setPassword("password123");
        assertThrows(IllegalArgumentException.class, () -> authService.login(blankUser));

        LoginRequest nullPass = new LoginRequest();
        nullPass.setUsername("student1");
        nullPass.setPassword(null);
        assertThrows(IllegalArgumentException.class, () -> authService.login(nullPass));

        LoginRequest blankPass = new LoginRequest();
        blankPass.setUsername("student1");
        blankPass.setPassword("   ");
        assertThrows(IllegalArgumentException.class, () -> authService.login(blankPass));
    }

    // 10. Student and instructor tokens contain authoritative roles
    @Test
    void testLogin_AuthoritativeRoles_StudentAndInstructor() {
        AuthUser student = new AuthUser();
        student.setUsername("student_user");
        student.setPassword(passwordEncoder.encode("pass"));
        student.setRole("STUDENT");

        AuthUser instructor = new AuthUser();
        instructor.setUsername("prof_user");
        instructor.setPassword(passwordEncoder.encode("pass"));
        instructor.setRole("INSTRUCTOR");

        when(authUserRepository.findByUsernameIgnoreCase("student_user")).thenReturn(Optional.of(student));
        when(authUserRepository.findByUsernameIgnoreCase("prof_user")).thenReturn(Optional.of(instructor));

        when(jwtService.generateToken("student_user", "STUDENT")).thenReturn("student-jwt");
        when(jwtService.generateToken("prof_user", "INSTRUCTOR")).thenReturn("instructor-jwt");

        LoginRequest studentReq = new LoginRequest();
        studentReq.setUsername("student_user");
        studentReq.setPassword("pass");
        LoginResponse studentRes = authService.login(studentReq);
        assertEquals("STUDENT", studentRes.getRole());
        assertEquals("student-jwt", studentRes.getToken());

        LoginRequest profReq = new LoginRequest();
        profReq.setUsername("prof_user");
        profReq.setPassword("pass");
        LoginResponse profRes = authService.login(profReq);
        assertEquals("INSTRUCTOR", profRes.getRole());
        assertEquals("instructor-jwt", profRes.getToken());
    }

    // 11. Real JwtService validation: token contains required claims
    @Test
    void testRealJwtService_GeneratesAndExtractsValidClaims() throws Exception {
        JwtService realJwtService = new JwtService();
        Field secretField = JwtService.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(realJwtService, "AcademiaXSecretKeyForJWTAuthentication2026");

        String token = realJwtService.generateToken("2400032678", "STUDENT");
        assertNotNull(token);
        assertFalse(token.isBlank());

        String extractedUser = realJwtService.extractUsername(token);
        assertEquals("2400032678", extractedUser);
    }

    // 12. Single authentication token: Only one field named 'token', no accessToken or refreshToken
    @Test
    void testLogin_ReturnsOnlySingleTokenNamedToken_NoAccessTokenOrRefreshToken() {
        AuthUser user = new AuthUser();
        user.setId(3L);
        user.setUsername("singletokenuser");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRole("STUDENT");

        when(authUserRepository.findByUsernameIgnoreCase("singletokenuser")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("singletokenuser", "STUDENT")).thenReturn("single-jwt-token-xyz");

        LoginRequest req = new LoginRequest();
        req.setUsername("singletokenuser");
        req.setPassword("password123");

        LoginResponse res = authService.login(req);

        // 1. Verify single token field exists and contains the JWT
        assertNotNull(res.getToken());
        assertEquals("single-jwt-token-xyz", res.getToken());

        // 2. Verify no accessToken or refreshToken fields exist in LoginResponse class or superclass
        for (Field f : LoginResponse.class.getDeclaredFields()) {
            assertFalse(f.getName().equalsIgnoreCase("accessToken"), "LoginResponse must not contain accessToken field");
            assertFalse(f.getName().equalsIgnoreCase("refreshToken"), "LoginResponse must not contain refreshToken field");
        }
        for (Field f : soa.authservice.dto.AuthResponse.class.getDeclaredFields()) {
            assertFalse(f.getName().equalsIgnoreCase("accessToken"), "AuthResponse must not contain accessToken field");
            assertFalse(f.getName().equalsIgnoreCase("refreshToken"), "AuthResponse must not contain refreshToken field");
        }
    }

    // 13. Configurable JWT expiration via environment/properties
    @Test
    void testJwtExpiration_ConfigurableViaProperties() throws Exception {
        JwtService customJwtService = new JwtService();
        Field secretField = JwtService.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(customJwtService, "AcademiaXSecretKeyForJWTAuthentication2026");

        Field expField = JwtService.class.getDeclaredField("jwtExpirationMs");
        expField.setAccessible(true);
        expField.setLong(customJwtService, 3600000L); // 1 hour configured

        assertEquals(3600000L, customJwtService.getJwtExpirationMs());

        String token = customJwtService.generateToken("user1", "STUDENT");
        assertNotNull(token);
        assertEquals("user1", customJwtService.extractUsername(token));
    }

    // 14. Expired token rejection
    @Test
    void testJwtExpiredToken_ThrowsExpiredJwtException() throws Exception {
        JwtService shortLivedJwtService = new JwtService();
        Field secretField = JwtService.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(shortLivedJwtService, "AcademiaXSecretKeyForJWTAuthentication2026");

        Field expField = JwtService.class.getDeclaredField("jwtExpirationMs");
        expField.setAccessible(true);
        expField.setLong(shortLivedJwtService, -1000L); // Negative expiration -> already expired

        String expiredToken = shortLivedJwtService.generateToken("user_expired", "STUDENT");
        assertNotNull(expiredToken);

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> shortLivedJwtService.extractUsername(expiredToken));
    }
}
