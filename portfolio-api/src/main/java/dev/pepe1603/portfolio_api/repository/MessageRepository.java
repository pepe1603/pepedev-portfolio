package dev.pepe1603.portfolio_api.repository;

import dev.pepe1603.portfolio_api.entity.Message;
import dev.pepe1603.portfolio_api.enums.MessageStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByStatus(MessageStatus status, Sort sort);
}