package soa.userservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import soa.userservice.dto.UserRequest;
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
        user.setId(2400032678L);
        user.setUserId("2400032678");
        user.setName("Alice");
        user.setEmail("alice@test.com");
        user.setRole("STUDENT");

        when(userRepository.findById(2400032678L)).thenReturn(Optional.of(user));

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
        user.setId(2400032678L);
        user.setUserId("2400032678");
        user.setName("Alice");

        when(userRepository.findById(2400032678L)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserById("2400032678");

        assertNotNull(response);
        assertEquals("2400032678", response.getUserId());
    }

    // --- REQUIREMENT 7 TESTS: Unique ID Generation, Duplicate Prevention, Integrity ---

    @Test
    void testCreateMultipleUsers_AllReceiveUniqueGeneratedIds() {
        AtomicLong idSequence = new AtomicLong(100L);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) {
                u.setId(idSequence.incrementAndGet());
            }
            return u;
        });

        List<UserResponse> createdUsers = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            UserRequest req = new UserRequest();
            req.setName("User " + i);
            req.setEmail("user" + i + "@academia.edu");
            req.setRole("STUDENT");
            req.setStudentId("STU" + i);
            createdUsers.add(userService.createUser(req));
        }

        // Verify all 5 receive distinct, unique primary key IDs
        assertEquals(5, createdUsers.size());
        Set<Long> ids = new HashSet<>();
        for (UserResponse resp : createdUsers) {
            assertNotNull(resp.getId());
            assertTrue(ids.add(resp.getId()), "Duplicate primary key ID detected: " + resp.getId());
            assertEquals(String.valueOf(resp.getId()), resp.getUserId());
        }
        assertEquals(5, ids.size());
    }

    @Test
    void testCreateUser_DoesNotAcceptClientProvidedId() {
        AtomicLong sequence = new AtomicLong(500L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) {
                u.setId(sequence.incrementAndGet());
            }
            return u;
        });

        UserRequest req1 = new UserRequest();
        req1.setName("Student One");
        req1.setEmail("stu1@test.com");
        req1.setRole("STUDENT");
        req1.setStudentId("MANUAL_SAME_ID"); // Client provides same studentId

        UserRequest req2 = new UserRequest();
        req2.setName("Student Two");
        req2.setEmail("stu2@test.com");
        req2.setRole("STUDENT");
        req2.setStudentId("MANUAL_SAME_ID"); // Client provides same studentId

        UserResponse resp1 = userService.createUser(req1);
        UserResponse resp2 = userService.createUser(req2);

        // Primary keys must be distinct database-generated IDs, not client-supplied values
        assertNotEquals(resp1.getId(), resp2.getId());
        assertEquals(501L, resp1.getId());
        assertEquals(502L, resp2.getId());
    }

    @Test
    void testCreateUser_DuplicateEmail_ThrowsIllegalStateException() {
        when(userRepository.existsByEmailIgnoreCase("duplicate@test.com")).thenReturn(true);

        UserRequest req = new UserRequest();
        req.setName("Duplicate User");
        req.setEmail("duplicate@test.com");
        req.setRole("STUDENT");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> userService.createUser(req));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testConcurrentCreation_ProducesUniqueIds() {
        AtomicLong sequence = new AtomicLong(1000L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) {
                u.setId(sequence.incrementAndGet());
            }
            return u;
        });

        Set<Long> generatedIds = ConcurrentHashMap.newKeySet();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            final int index = i;
            threads.add(new Thread(() -> {
                UserRequest req = new UserRequest();
                req.setName("Concurrent User " + index);
                req.setEmail("concurrent" + index + "@test.com");
                req.setRole("STUDENT");
                UserResponse resp = userService.createUser(req);
                generatedIds.add(resp.getId());
            }));
        }

        threads.forEach(Thread::start);
        threads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // All 20 concurrently created users must have 20 distinct IDs
        assertEquals(20, generatedIds.size());
    }

    @Test
    void testUpdateAndDelete_DoesNotAffectOtherUsers() {
        authenticateAs("admin", "ADMIN");

        User user1 = new User();
        user1.setId(10L);
        user1.setUserId("10");
        user1.setName("Original User 1");
        user1.setEmail("user1@test.com");

        User user2 = new User();
        user2.setId(20L);
        user2.setUserId("20");
        user2.setName("Original User 2");
        user2.setEmail("user2@test.com");

        when(userRepository.findById(10L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user2));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Update user 1
        UserUpdateRequest updateReq = new UserUpdateRequest();
        updateReq.setName("Updated User 1");
        UserResponse updated1 = userService.updateUser("10", updateReq);

        assertEquals("Updated User 1", updated1.getName());
        assertEquals("Original User 2", user2.getName()); // user 2 remains unchanged

        // Delete user 1
        userService.deleteUser("10");
        verify(userRepository).delete(user1);
        verify(userRepository, never()).delete(user2); // user 2 is not deleted
    }
}
