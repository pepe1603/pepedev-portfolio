package dev.pepe1603.api.exception;

public class LoginRateLimitedException extends RuntimeException implements RateLimitedException {

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Demasiados intentos de login");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public String getUserMessage() {
        return "Demasiados intentos de login. Inténtalo de nuevo más tarde";
    }
}
