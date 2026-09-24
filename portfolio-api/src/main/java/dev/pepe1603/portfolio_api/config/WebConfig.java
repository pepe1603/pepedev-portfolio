package dev.pepe1603.portfolio_api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    public WebConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = storageProperties.getDir();
        String location = "file:" + (dir.endsWith("/") ? dir : dir + "/");
        registry.addResourceHandler("/files/**").addResourceLocations(location);
    }
}