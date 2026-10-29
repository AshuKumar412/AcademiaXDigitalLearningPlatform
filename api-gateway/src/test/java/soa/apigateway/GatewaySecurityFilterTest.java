package soa.apigateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import soa.apigateway.filter.JwtAuthenticationFilter;

class GatewaySecurityFilterTest {

    private final String secret = "AcademiaXSecretKeyForJWTAuthentication2026";
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter();

        Field secretField = JwtAuthenticationFilter.class.getDeclaredField("secret");
        secretField.setAccessible(true);
        secretField.set(filter, secret);
    }

    private String generateToken(String username, String role, long expirationMillis) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()))
                .compact();
    }

    @Test
    void testMissingJwt_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/courses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("JWT token is required"));
    }

    @Test
    void testMalformedJwt_Returns401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/courses");
        request.addHeader("Authorization", "Bearer invalid.malformed.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid, expired, or tampered JWT"));
    }

    @Test
    void testExpiredJwt_Returns401() throws Exception {
        String expiredToken = generateToken("student1", "STUDENT", -10000); // 10s in the past

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/courses");
        request.addHeader("Authorization", "Bearer " + expiredToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid, expired, or tampered JWT"));
    }

    @Test
    void testTamperedSignatureJwt_Returns401() throws Exception {
        // Sign with a different key
        String badKey = "DifferentSecretKeyForTamperedAttack9999999999";
        String tamperedToken = Jwts.builder()
                .subject("attacker")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(badKey.getBytes()))
                .compact();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/courses");
        request.addHeader("Authorization", "Bearer " + tamperedToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("Invalid, expired, or tampered JWT"));
    }

    @Test
    void testForgedIdentityHeaders_AreStrippedAndReplacedWithJwtClaims() throws Exception {
        String validToken = generateToken("realStudent", "STUDENT", 60000);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/enrollments");
        request.addHeader("Authorization", "Bearer " + validToken);
        // Attacker attempts to forge admin headers
        request.addHeader("X-User-Id", "adminUser");
        request.addHeader("X-User-Role", "ADMIN");

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            HttpServletRequest httpReq = (HttpServletRequest) req;
            assertEquals("realStudent", httpReq.getHeader("X-User-Id"));
            assertEquals("STUDENT", httpReq.getHeader("X-User-Role"));
        });

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("realStudent", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void testPublicEndpoint_BypassesFilter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }
}
