package soa.paymentservice.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

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
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret:AcademiaXSecretKeyForJWTAuthentication2026}")
    private String secret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Allow actuator health checks without JWT
        if (path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check for trusted Gateway headers or Authorization Bearer token
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null) {
            authHeader = request.getHeader("authorization");
        }
        String gatewayUserId = request.getHeader("X-User-Id");
        String gatewayUserRole = request.getHeader("X-User-Role");

        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7).trim();
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
                        ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        : Collections.emptyList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Invalid or expired JWT token\"}");
                return;
            }
        } else if (gatewayUserId != null && !gatewayUserId.isBlank()) {
            String role = gatewayUserRole != null ? gatewayUserRole.trim().toUpperCase() : null;
            if (role != null && role.startsWith("ROLE_")) {
                role = role.substring(5);
            }
            List<SimpleGrantedAuthority> authorities = (role != null && !role.isBlank())
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    : Collections.emptyList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(gatewayUserId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
