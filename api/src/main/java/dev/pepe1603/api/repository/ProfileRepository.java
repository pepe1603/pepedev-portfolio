package dev.pepe1603.api.repository;

import dev.pepe1603.api.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, Short> {
}