package dev.pepe1603.portfolio_api.controller;

import dev.pepe1603.portfolio_api.dto.admin.MessageCountResponse;
import dev.pepe1603.portfolio_api.dto.admin.PageResponse;
import dev.pepe1603.portfolio_api.entity.Message;
import dev.pepe1603.portfolio_api.enums.MessageStatus;
import dev.pepe1603.portfolio_api.service.AdminMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/messages")
public class AdminMessageController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort INBOX_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"));

    private final AdminMessageService messageService;

    public AdminMessageController(AdminMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public PageResponse<Message> list(@RequestParam(required = false) MessageStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "parámetro 'page' debe ser >= 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "parámetro 'size' debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        Pageable pageable = PageRequest.of(page, size, INBOX_SORT);
        Page<Message> result = messageService.list(status, pageable);
        return PageResponse.of(result);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Mensajes no leídos", description = "Contador de mensajes con status NEW (para el badge del panel).")
    @ApiResponse(responseCode = "200", description = "{ \"count\": N }")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public MessageCountResponse unreadCount() {
        return new MessageCountResponse(messageService.unreadCount());
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