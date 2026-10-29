package soa.authservice.dto;

public class LoginResponse extends AuthResponse {

    public LoginResponse() {
        super();
    }

    public LoginResponse(String token, String username, String role) {
        super();
        setToken(token);
        setUsername(username);
        setRole(role);
    }
}
