package soa.authservice.dto;

public class LoginResponse extends AuthResponse {

    private boolean success;
    private String message;
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private UserSummary user;

    public LoginResponse() {
        this.tokenType = "Bearer";
        this.expiresIn = 604800L;
    }

    public static class UserSummary {
        private String userId;
        private String username;
        private String role;

        public UserSummary() {
        }

        public UserSummary(String userId, String username, String role) {
            this.userId = userId;
            this.username = username;
            this.role = role;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        super.setToken(accessToken);
    }

    @Override
    public void setToken(String token) {
        super.setToken(token);
        this.accessToken = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserSummary getUser() {
        return user;
    }

    public void setUser(UserSummary user) {
        this.user = user;
    }
}
