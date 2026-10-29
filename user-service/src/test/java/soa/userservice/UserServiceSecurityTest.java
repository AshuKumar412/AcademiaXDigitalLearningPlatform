package soa.userservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import soa.userservice.dto.UserResponse;
import soa.userservice.dto.UserUpdateRequest;
import soa.userservice.model.User;
import soa.userservice.repository.UserRepository;
import soa.userservice.service.UserService;

class UserServiceSecurityTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void testGetUserById_OwnProfile_Succeeds() {
        authenticateAs("2400032678", "STUDENT");

        User user = new User();
        user.setUserId("2400032678");
        user.setName("Alice");
        user.setEmail("alice@test.com");
        user.setRole("STUDENT");

        when(userRepository.findById("2400032678")).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById("2400032678");

        assertNotNull(response);
        assertEquals("2400032678", response.getUserId());
        assertEquals("Alice", response.getName());
    }

    @Test
    void testGetUserById_AnotherUsersProfile_ThrowsAccessDenied() {
        authenticateAs("2400032678", "STUDENT");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.getUserById("2400039999"));
        assertEquals("Users can only access their own profile", ex.getMessage());
    }

    @Test
    void testUpdateUser_AnotherUsersProfile_ThrowsAccessDenied() {
        authenticateAs("2400032678", "STUDENT");

        UserUpdateRequest request = new UserUpdateRequest();
        request.setName("Hacked Name");

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.updateUser("2400039999", request));
        assertEquals("Users can only access their own profile", ex.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testGetUserById_AdminAccess_Succeeds() {
        authenticateAs("admin_user", "ADMIN");

        User user = new User();
        user.setUserId("2400032678");
        user.setName("Alice");

        when(userRepository.findById("2400032678")).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById("2400032678");

        assertNotNull(response);
        assertEquals("2400032678", response.getUserId());
    }
}
