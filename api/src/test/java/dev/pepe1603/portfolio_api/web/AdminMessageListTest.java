package dev.pepe1603.portfolio_api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pepe1603.portfolio_api.exception.ApiExceptionHandler;
import dev.pepe1603.portfolio_api.config.StorageProperties;
import dev.pepe1603.portfolio_api.controller.AdminMessageController;
import dev.pepe1603.portfolio_api.entity.Message;
import dev.pepe1603.portfolio_api.enums.MessageStatus;
import dev.pepe1603.portfolio_api.security.JwtTokenService;
import dev.pepe1603.portfolio_api.security.TokenBlacklist;
import dev.pepe1603.portfolio_api.service.AdminMessageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(controllers = AdminMessageController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, StorageProperties.class})
@TestPropertySource(properties = {
        "APP_STORAGE_PUBLIC_URL=http://localhost:8080/files"})
class AdminMessageListTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminMessageService messageService;
    @MockitoBean
    private JwtTokenService jwtTokenService;
    @MockitoBean
    private TokenBlacklist tokenBlacklist;

    private Message sample(int createdAtMillis) {
        Message message = new Message();
        message.setId(UUID.randomUUID());
        message.setName("Pepe");
        message.setEmail("a@b.es");
        message.setSubject("Hola");
        message.setBody("Mensaje de contacto");
        message.setStatus(MessageStatus.NEW);
        message.setCreatedAt(Instant.ofEpochMilli(createdAtMillis));
        return message;
    }

    private void stubList(Pageable pageable) {
        given(messageService.list(null, pageable)).willReturn(new PageImpl<>(
                List.of(sample(2000), sample(1000)), pageable, 2));
    }

    @Test
    void listaPaginaPorDefectoConOrdenDeterminista() throws Exception {
        stubList(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        mockMvc.perform(get("/admin/messages"))
                .andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.items.length()").value(2),
                        jsonPath("$.items[0].status").value("NEW"),
                        jsonPath("$.items[0].body").value("Mensaje de contacto"),
                        jsonPath("$.page").value(0),
                        jsonPath("$.size").value(20),
                        jsonPath("$.totalElements").value(2),
                        jsonPath("$.totalPages").value(1),
                        jsonPath("$.last").value(true));
    }

    @Test
    void aplicaOrdenCreatedAtDESCIdDESC() throws Exception {
        stubList(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        mockMvc.perform(get("/admin/messages")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(messageService).list(org.mockito.Mockito.eq(null), captor.capture());
        assertThat(captor.getValue().getSort().toString())
                .isEqualTo("createdAt: DESC,id: DESC");
    }

    @Test
    void paginaYSizePersonalizadosLleganAlServicio() throws Exception {
        Pageable pageable = PageRequest.of(2, 50, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        given(messageService.list(null, pageable)).willReturn(
                new PageImpl<>(List.of(), pageable, 150));

        mockMvc.perform(get("/admin/messages").param("page", "2").param("size", "50"))
                .andExpect(status().isOk())
                .andExpectAll(
                        jsonPath("$.items.length()").value(0),
                        jsonPath("$.page").value(2),
                        jsonPath("$.size").value(50),
                        jsonPath("$.totalElements").value(150),
                        jsonPath("$.last").value(true));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(messageService).list(org.mockito.Mockito.eq(null), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    void statusSePropagaAlServicio() throws Exception {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        given(messageService.list(MessageStatus.ARCHIVED, pageable)).willReturn(new PageImpl<>(
                List.of(sample(3000)), pageable, 1));

        mockMvc.perform(get("/admin/messages").param("status", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void pageNegativa_400() throws Exception {
        mockMvc.perform(get("/admin/messages").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.title").value("Bad Request"),
                        jsonPath("$.status").value(400),
                        jsonPath("$.detail").value("parámetro 'page' debe ser >= 0"),
                        jsonPath("$.instance").value("/admin/messages"));
    }

    @Test
    void sizeFueraDeRango_400() throws Exception {
        mockMvc.perform(get("/admin/messages").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.status").value(400),
                        jsonPath("$.detail").value("parámetro 'size' debe estar entre 1 y 100"));
    }

    @Test
    void statusInvalido_400TypeMismatch() throws Exception {
        mockMvc.perform(get("/admin/messages").param("status", "oof"))
                .andExpect(status().isBadRequest())
                .andExpectAll(
                        jsonPath("$.status").value(400),
                        jsonPath("$.instance").value("/admin/messages"));
    }

    @Test
    void unreadCountDevuelveElContador() throws Exception {
        given(messageService.unreadCount()).willReturn(4L);

        mockMvc.perform(get("/admin/messages/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(4));
    }
}