package dev.pepe1603.portfolio_api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import dev.pepe1603.portfolio_api.config.PublicCacheProperties;

@Service
public class PublicCacheService {

    private static final String KEY_PREFIX = "pub:";

    private final RedisTemplate<String, Object> redis;
    private final PublicCacheProperties properties;

    public PublicCacheService(RedisTemplate<String, Object> redis, PublicCacheProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Optional<String> getProfile(String lang) {
        return get(key("profile", lang));
    }

    public void putProfile(String lang, String body) {
        put(key("profile", lang), body);
    }

    public Optional<String> getProjectsList(String lang) {
        return get(key("projects", lang));
    }

    public void putProjectsList(String lang, String body) {
        put(key("projects", lang), body);
    }

    public Optional<String> getProject(String slug, String lang) {
        return get(key("project", slug, lang));
    }

    public void putProject(String slug, String lang, String body) {
        put(key("project", slug, lang), body);
    }

    public void evictProfile() {
        redis.delete(List.of(key("profile", "es"), key("profile", "en")));
    }

    public void evictProjectsList() {
        redis.delete(List.of(key("projects", "es"), key("projects", "en")));
    }

    public void evictProject(String slug) {
        redis.delete(List.of(key("project", slug, "es"), key("project", slug, "en")));
    }

    public String etagFor(String body) {
        MessageDigest sha256 = sha256();
        String hex = HexFormat.of().formatHex(sha256.digest(body.getBytes(StandardCharsets.UTF_8)));
        return "\"" + hex + "\"";
    }

    private Optional<String> get(String key) {
        if (redis.opsForValue().get(key) instanceof String body) {
            return Optional.of(body);
        }
        return Optional.empty();
    }

    private void put(String key, String body) {
        redis.opsForValue().set(key, body, Duration.ofSeconds(properties.getTtlSeconds()));
    }

    private static String key(String... parts) {
        return KEY_PREFIX + String.join(":", parts);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible en este JVM", e);
        }
    }
}