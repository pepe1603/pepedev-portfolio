package dev.pepe1603.portfolio_api.repository;

import dev.pepe1603.portfolio_api.entity.Message;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {
}