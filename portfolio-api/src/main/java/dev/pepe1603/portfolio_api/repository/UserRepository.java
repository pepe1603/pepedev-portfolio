package dev.pepe1603.portfolio_api.repository;

import dev.pepe1603.portfolio_api.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
}