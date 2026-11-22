package dev.pepe1603.api.repository;

import dev.pepe1603.api.entity.Message;
import dev.pepe1603.api.enums.MessageStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByStatus(MessageStatus status, Sort sort);

    Page<Message> findByStatus(MessageStatus status, Pageable pageable);

    long countByStatus(MessageStatus status);
}