package dev.pepe1603.portfolio_api.exception;

public class ContactRateLimitedException extends RuntimeException implements RateLimitedException {

    private final long retryAfterSeconds;

    public ContactRateLimitedException(long retryAfterSeconds) {
        super("Demasiados intentos de contacto");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public String getUserMessage() {
        return "Demasiados envíos de contacto. Inténtalo de nuevo más tarde";
    }
}
