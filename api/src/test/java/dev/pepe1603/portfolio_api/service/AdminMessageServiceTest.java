package dev.pepe1603.portfolio_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.pepe1603.portfolio_api.entity.Message;
import dev.pepe1603.portfolio_api.enums.AuditAction;
import dev.pepe1603.portfolio_api.enums.AuditResource;
import dev.pepe1603.portfolio_api.enums.MessageStatus;
import dev.pepe1603.portfolio_api.repository.MessageRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminMessageServiceTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminMessageService service =
            new AdminMessageService(messageRepository, auditService);

    @Test
    void unreadCountDevuelveElContadorDeNuevos() {
        given(messageRepository.countByStatus(MessageStatus.NEW)).willReturn(3L);

        assertThat(service.unreadCount()).isEqualTo(3L);
        verify(messageRepository).countByStatus(MessageStatus.NEW);
    }

    @Test
    void markReadMarcaLeidoYAudita() {
        UUID id = UUID.randomUUID();
        Message message = new Message();
        message.setId(id);
        message.setEmail("cliente@test.es");
        message.setStatus(MessageStatus.NEW);
        given(messageRepository.findById(id)).willReturn(Optional.of(message));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        Message saved = service.markRead(id);

        assertThat(saved.getStatus()).isEqualTo(MessageStatus.READ);
        verify(auditService).record(eq(AuditAction.READ), eq(AuditResource.MESSAGE), eq(id), anyString());
    }

    @Test
    void markArchivedArchivaYAudita() {
        UUID id = UUID.randomUUID();
        Message message = new Message();
        message.setId(id);
        message.setStatus(MessageStatus.READ);
        given(messageRepository.findById(id)).willReturn(Optional.of(message));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> inv.getArgument(0));

        Message saved = service.markArchived(id);

        assertThat(saved.getStatus()).isEqualTo(MessageStatus.ARCHIVED);
        verify(auditService).record(eq(AuditAction.ARCHIVE), eq(AuditResource.MESSAGE), eq(id), anyString());
    }

    @Test
    void deleteBorraYAudita() {
        UUID id = UUID.randomUUID();
        Message message = new Message();
        message.setId(id);
        message.setEmail("cliente@test.es");
        given(messageRepository.findById(id)).willReturn(Optional.of(message));

        service.delete(id);

        verify(messageRepository).delete(message);
        verify(auditService).record(eq(AuditAction.DELETE), eq(AuditResource.MESSAGE), eq(id), anyString());
    }
}