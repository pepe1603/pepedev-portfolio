package dev.pepe1603.portfolio_api.exception;

/**
 * Lo común a los 429: cuánto esperar y qué contarle al usuario. Así el manejador no necesita un
 * {@code if} por cadaEndpoint con rate limit.
 */
public interface RateLimitedException {

    long getRetryAfterSeconds();

    String getUserMessage();
}
