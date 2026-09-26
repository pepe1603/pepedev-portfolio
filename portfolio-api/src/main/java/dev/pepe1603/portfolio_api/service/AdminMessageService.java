package dev.pepe1603.portfolio_api.service;

import dev.pepe1603.portfolio_api.entity.Message;
import dev.pepe1603.portfolio_api.enums.MessageStatus;
import dev.pepe1603.portfolio_api.repository.MessageRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminMessageService {

    private final MessageRepository messageRepository;

    public AdminMessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public Page<Message> list(MessageStatus status, Pageable pageable) {
        if (status == null) {
            return messageRepository.findAll(pageable);
        }
        return messageRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Message getById(UUID id) {
        return findOrThrow(id);
    }

    @Transactional
    public Message markRead(UUID id) {
        Message message = findOrThrow(id);
        message.setStatus(MessageStatus.READ);
        return messageRepository.save(message);
    }

    @Transactional
    public Message markArchived(UUID id) {
        Message message = findOrThrow(id);
        message.setStatus(MessageStatus.ARCHIVED);
        return messageRepository.save(message);
    }

    @Transactional
    public void delete(UUID id) {
        Message message = findOrThrow(id);
        messageRepository.delete(message);
    }

    private Message findOrThrow(UUID id) {
        return messageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
    }
}