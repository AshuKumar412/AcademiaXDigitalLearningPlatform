package soa.userservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import soa.userservice.dto.UserRequest;
import soa.userservice.dto.UserResponse;
import soa.userservice.dto.UserUpdateRequest;
import soa.userservice.exception.UserNotFoundException;
import soa.userservice.model.User;
import soa.userservice.repository.UserRepository;
import soa.userservice.service.UserService;

@SpringBootTest
class UserIdUniquenessIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    // 1. Create at least five different users and verify all five receive different generated IDs
    @Test
    void testCreateFiveUsers_AllReceiveUniqueDatabaseGeneratedIds() {
        List<UserResponse> responses = new ArrayList<>();
        String prefix = "test5_" + UUID.randomUUID().toString().substring(0, 6);

        for (int i = 1; i <= 5; i++) {
            UserRequest req = new UserRequest();
            req.setName("Test Student " + i);
            req.setEmail(prefix + "_user" + i + "@academia.edu");
            req.setPhone("123456789" + i);
            req.setRole("STUDENT");
            req.setDepartment("Computer Science");
            req.setStudentId("STU_" + prefix + "_" + i);

            UserResponse res = userService.createUser(req);
            assertNotNull(res);
            assertNotNull(res.getId(), "Database-generated ID must not be null");
            assertNotNull(res.getUserId(), "UserId must not be null");
            responses.add(res);
        }

        assertEquals(5, responses.size());
        Set<Long> uniqueIds = new HashSet<>();
        for (UserResponse r : responses) {
            assertTrue(uniqueIds.add(r.getId()), "Encountered duplicate ID: " + r.getId());
            assertEquals(String.valueOf(r.getId()), r.getUserId());
        }
        assertEquals(5, uniqueIds.size(), "All 5 users must have distinct IDs");
    }

    // 2. Verify that IDs remain unique under concurrent user creation
    @Test
    void testConcurrentCreation_ProducesUniqueDatabaseIds() throws InterruptedException {
        int threadCount = 10;
        String prefix = "conc_" + UUID.randomUUID().toString().substring(0, 6);
        Set<Long> createdIds = ConcurrentHashMap.newKeySet();
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads.add(new Thread(() -> {
                try {
                    UserRequest req = new UserRequest();
                    req.setName("Concurrent User " + index);
                    req.setEmail(prefix + "_conc" + index + "@academia.edu");
                    req.setRole("STUDENT");
                    UserResponse res = userService.createUser(req);
                    createdIds.add(res.getId());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }));
        }

        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.join();
        }

        assertEquals(threadCount, createdIds.size(), "Every concurrent request must generate a distinct primary key");
    }

    // 3. Retrieve each user by returned ID and confirm the correct record is returned
    @Test
    void testRetrieveEachUserByReturnedId() {
        String prefix = "ret_" + UUID.randomUUID().toString().substring(0, 6);

        UserRequest req1 = new UserRequest();
        req1.setName("Alice Verification");
        req1.setEmail(prefix + "_alice@academia.edu");
        req1.setRole("STUDENT");
        UserResponse created1 = userService.createUser(req1);

        UserRequest req2 = new UserRequest();
        req2.setName("Bob Verification");
        req2.setEmail(prefix + "_bob@academia.edu");
        req2.setRole("INSTRUCTOR");
        UserResponse created2 = userService.createUser(req2);

        // Fetch by numeric ID string
        UserResponse fetched1 = userService.getUserById(String.valueOf(created1.getId()));
        assertEquals(created1.getId(), fetched1.getId());
        assertEquals("Alice Verification", fetched1.getName());
        assertEquals("STUDENT", fetched1.getRole());

        UserResponse fetched2 = userService.getUserById(String.valueOf(created2.getId()));
        assertEquals(created2.getId(), fetched2.getId());
        assertEquals("Bob Verification", fetched2.getName());
        assertEquals("INSTRUCTOR", fetched2.getRole());

        assertNotEquals(fetched1.getId(), fetched2.getId());
    }

    // 4. Verify updating and deleting one user does not affect another user
    @Test
    void testUpdateAndDelete_DoesNotAffectOtherUsers() {
        String prefix = "iso_" + UUID.randomUUID().toString().substring(0, 6);

        UserRequest reqA = new UserRequest();
        reqA.setName("User A Initial");
        reqA.setEmail(prefix + "_a@test.com");
        reqA.setRole("STUDENT");
        UserResponse userA = userService.createUser(reqA);

        UserRequest reqB = new UserRequest();
        reqB.setName("User B Initial");
        reqB.setEmail(prefix + "_b@test.com");
        reqB.setRole("STUDENT");
        UserResponse userB = userService.createUser(reqB);

        // Update user A
        UserUpdateRequest updateReq = new UserUpdateRequest();
        updateReq.setName("User A Modified");
        updateReq.setDepartment("Mathematics");
        UserResponse updatedA = userService.updateUser(String.valueOf(userA.getId()), updateReq);

        assertEquals("User A Modified", updatedA.getName());

        // Confirm user B remains untouched
        UserResponse checkB = userService.getUserById(String.valueOf(userB.getId()));
        assertEquals("User B Initial", checkB.getName());

        // Delete user A
        userService.deleteUser(String.valueOf(userA.getId()));

        // Confirm user A is gone
        assertThrows(UserNotFoundException.class, () -> userService.getUserById(String.valueOf(userA.getId())));

        // Confirm user B is STILL present and intact
        UserResponse survivorB = userService.getUserById(String.valueOf(userB.getId()));
        assertNotNull(survivorB);
        assertEquals(userB.getId(), survivorB.getId());
        assertEquals("User B Initial", survivorB.getName());
    }

    // 5. Test duplicate email handling and database constraints
    @Test
    void testDuplicateEmailHandling_RejectedSafely() {
        String email = "dup_" + UUID.randomUUID().toString().substring(0, 6) + "@test.com";

        UserRequest req1 = new UserRequest();
        req1.setName("Original User");
        req1.setEmail(email);
        req1.setRole("STUDENT");
        userService.createUser(req1);

        UserRequest req2 = new UserRequest();
        req2.setName("Imposter User");
        req2.setEmail(email);
        req2.setRole("STUDENT");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> userService.createUser(req2));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    // 6. Test existsById endpoint logic
    @Test
    void testExistsById() {
        String prefix = "ex_" + UUID.randomUUID().toString().substring(0, 6);

        UserRequest req = new UserRequest();
        req.setName("Existing User");
        req.setEmail(prefix + "@test.com");
        req.setRole("STUDENT");
        UserResponse res = userService.createUser(req);

        assertTrue(userService.existsById(String.valueOf(res.getId())));
        assertFalse(userService.existsById("999999999"));
    }
}
