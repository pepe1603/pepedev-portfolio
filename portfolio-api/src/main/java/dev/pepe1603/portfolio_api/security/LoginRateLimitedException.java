package dev.pepe1603.portfolio_api.security;

public class LoginRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Demasiados intentos de login");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}