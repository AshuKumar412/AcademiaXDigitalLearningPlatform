package soa.apigateway.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String secret;

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.trim().isEmpty() || secret.length() < 32) {
            throw new IllegalStateException("JWT signing secret must be configured and be at least 32 characters long");
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Public endpoints limited to: /auth/login, /auth/register, /actuator/health, /gateway/status
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null) {
            authorizationHeader = request.getHeader("authorization");
        }

        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT token is required");
            return;
        }

        String token = authorizationHeader.substring(7).trim();

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                username = claims.get("userId", String.class);
            }
            if (username == null || username.isBlank()) {
                username = claims.get("username", String.class);
            }

            if (username == null || username.isBlank()) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid JWT: missing subject claim");
                return;
            }

            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                Object rolesObj = claims.get("roles");
                if (rolesObj instanceof List<?> list && !list.isEmpty()) {
                    role = list.get(0).toString();
                } else if (rolesObj instanceof String str) {
                    role = str;
                }
            }
            if (role == null || role.isBlank()) {
                role = claims.get("authorities", String.class);
            }

            if (role != null) {
                role = role.trim().toUpperCase();
                if (role.startsWith("ROLE_")) {
                    role = role.substring(5);
                }
            }

            List<SimpleGrantedAuthority> authorities = (role != null && !role.isBlank())
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role), new SimpleGrantedAuthority(role))
                    : Collections.emptyList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Wrap request: strip any client-supplied forged identity headers and inject authoritative claims
            HeaderModifierRequestWrapper wrappedRequest = new HeaderModifierRequestWrapper(request);
            wrappedRequest.addHeader("X-User-Id", username);
            if (role != null) {
                wrappedRequest.addHeader("X-User-Role", role);
            }
            wrappedRequest.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);

            filterChain.doFilter(wrappedRequest, response);

        } catch (Exception exception) {
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid, expired, or tampered JWT token");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }

    private boolean isPublicEndpoint(String path) {
        return path.equals("/auth/login") || path.startsWith("/auth/login/")
                || path.equals("/auth/register") || path.startsWith("/auth/register/")
                || path.equals("/actuator/health") || path.startsWith("/actuator/health/")
                || path.equals("/gateway") || path.startsWith("/gateway/")
                || path.equals("/gateway/status") || path.startsWith("/gateway/status/");
    }

    // Custom wrapper that sanitizes external identity headers and injects trusted gateway headers
    public static class HeaderModifierRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> customHeaders = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        public HeaderModifierRequestWrapper(HttpServletRequest request) {
            super(request);
        }

        public void addHeader(String name, String value) {
            this.customHeaders.put(name, value);
        }

        @Override
        public String getHeader(String name) {
            if (customHeaders.containsKey(name)) {
                return customHeaders.get(name);
            }
            // Strip client-supplied forged identity headers
            if ("X-User-Id".equalsIgnoreCase(name) || "X-User-Role".equalsIgnoreCase(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Enumeration<String> originalNames = super.getHeaderNames();
            while (originalNames != null && originalNames.hasMoreElements()) {
                String name = originalNames.nextElement();
                if (!"X-User-Id".equalsIgnoreCase(name) && !"X-User-Role".equalsIgnoreCase(name)) {
                    names.add(name);
                }
            }
            names.addAll(customHeaders.keySet());
            return Collections.enumeration(names);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (customHeaders.containsKey(name)) {
                return Collections.enumeration(List.of(customHeaders.get(name)));
            }
            if ("X-User-Id".equalsIgnoreCase(name) || "X-User-Role".equalsIgnoreCase(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }
    }
}