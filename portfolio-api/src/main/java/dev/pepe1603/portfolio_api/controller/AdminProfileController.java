package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.entity.Profile;
import dev.pepe1603.portfolio_api.service.AdminProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/profile")
public class AdminProfileController {

    private final AdminProfileService profileService;

    public AdminProfileController(AdminProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public Profile get() {
        return profileService.get();
    }
}