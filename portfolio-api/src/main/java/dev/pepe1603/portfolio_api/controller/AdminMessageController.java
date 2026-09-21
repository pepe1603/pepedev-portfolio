package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.entity.Message;
import dev.pepe1603.portfolio_api.enums.MessageStatus;
import dev.pepe1603.portfolio_api.service.AdminMessageService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/messages")
public class AdminMessageController {

    private final AdminMessageService messageService;

    public AdminMessageController(AdminMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public List<Message> list(@RequestParam(required = false) MessageStatus status) {
        return status == null ? messageService.listAll() : messageService.listByStatus(status);
    }

    @GetMapping("/{id}")
    public Message get(@PathVariable UUID id) {
        return messageService.getById(id);
    }

    @PatchMapping("/{id}/read")
    public Message markRead(@PathVariable UUID id) {
        return messageService.markRead(id);
    }

    @PatchMapping("/{id}/archive")
    public Message markArchived(@PathVariable UUID id) {
        return messageService.markArchived(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        messageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}