package dev.pepe1603.portfolio_api.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class StorageProperties {

    @Value("${APP_STORAGE_DIR}")
    private String dir;

    @Value("${APP_STORAGE_PUBLIC_URL:http://localhost:8080/files}")
    private String publicUrl;
}