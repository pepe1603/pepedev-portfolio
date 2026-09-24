package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.entity.Profile;
import dev.pepe1603.portfolio_api.repository.ProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminProfileService {

    private final ProfileRepository profileRepository;

    public AdminProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public Profile get() {
        return profileRepository.findById((short) 1)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil no encontrado"));
    }
}