package dev.pepe1603.portfolio_api.exception;

public class ContactRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public ContactRateLimitedException(long retryAfterSeconds) {
        super("Demasiados intentos de contacto");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}