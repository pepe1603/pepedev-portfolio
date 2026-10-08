package dev.pepe1603.api.exception;

public class OtpResendTooSoonException extends RuntimeException implements RateLimitedException {

    private final long retryAfterSeconds;

    public OtpResendTooSoonException(long retryAfterSeconds) {
        super("Reenvío de código OTP demasiado frecuente");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public String getUserMessage() {
        return "Ya se reenvió el código hace poco. Espera unos segundos antes de volver a pedirlo";
    }
}
