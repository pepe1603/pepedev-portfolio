package dev.pepe1603.api.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class PublicCacheProperties {

    @Value("${APP_PUBLIC_CACHE_TTL:300}")
    private long ttlSeconds;
}