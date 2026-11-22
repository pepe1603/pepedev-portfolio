package dev.pepe1603.api.config;

import dev.pepe1603.api.entity.User;
import dev.pepe1603.api.enums.UserRole;
import dev.pepe1603.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${APP_ADMIN_EMAIL}")
    private String adminEmail;

    @Value("${APP_ADMIN_SECRET}")
    private String adminSecret;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminSecret));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
    }
}