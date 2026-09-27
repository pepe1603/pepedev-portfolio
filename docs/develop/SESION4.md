# Sesión 4 — Rama `develop`: acuse al remitente + i18n es/en del email

> Continuación de la Sesión 3 (plantillas HTML con Thymeleaf). Backlog y acta en `docs/develop/`.

## Contexto

- Sesión 3: la notificación al admin es HTML vía Thymeleaf (`templates/mail/contact.html`),
  `multipart/alternative` explícito, best-effort async. En **español fijo**.
- Objetivo de la sesión:
  1. **Acuse al remitente**: al enviar el formulario, el visitante recibe un email de
     confirmación (`mail/ack.html`), mismo patrón multipart/alternative.
  2. **i18n `es`/`en`** de los textos de ambas plantillas con bundles (`messages_*.properties`)
     y `#{}` de Thymeleaf.

## Decisiones de diseño

- **Acuse**: gobernado por `APP_CONTACT_SEND_ACK` (default `true`, property `.env`). Si
  `false`, se salta solo el acuse (la notificación al admin sigue).
- **Idioma del acuse**: `ServletRequest.getLocale()` (Accept-Language del navegador) → si es
  `en` se usa inglés; **cualquier otro → `es`** (fallback). La API no toca su contrato.
- **Idioma de la notificación al admin**: fija en `es` (el administrador lee español).
- **Bundles**: `messages.properties` en castellano como bundle por defecto + `messages_en.properties`
  para inglés. Config `spring.messages.basename=messages` + encoding UTF-8.
- **Resolución de textos**: Thymeleaf `#{mail.…}` (el auto-config de Boot cablea el `MessageSource`
  al `SpringTemplateEngine`). El asunto del acuse se resuelve con `MessageSource` en
  `ContactService` (no va por plantilla).
- **Contexto de Thymeleaf**: se pasa `Locale` al `Context` del renderer.
- **Persistencia/rate limit**: intactos. El acuse es un segundo correo best-effort async tras
  persistir; si falla, sigue `201`. El control de flood lo sigue dando el rate limiter de contacto
  (1 solo submit → acuse ligado al mismo submit).
- **Escapado**: igual que Sesión 3 — `th:text` (auto-escape), nunca `th:utext` con datos de usuario.

## Lista de tareas (backlog de la sesión)

| Pieza | Tarea | Estado |
|---|---|---|
| P4-0 | Este documento (backlog + decisiones) | ⬜ |
| P4-1 | i18n de la notificación: bundles `messages`/`messages_en`, `#{}` en `templates/mail/contact.html`, config `spring.messages`, renderer con `Locale` + tests (render es/en) | ⬜ |
| P4-2 | Plantilla `templates/mail/ack.html` + `MailTemplateRenderer.renderAckHtml(vars, locale)` + tests (campos + escape + idioma) | ⬜ |
| P4-3 | Cablear acuse en `ContactService` (`APP_CONTACT_SEND_ACK`, `MessageSource` para asunto, locale por petición) + `ContactController` pasa `Locale` + tests `MimeMessage` (énvio acuse y notificación, guard si ack=false, subject en) | ⬜ |
| P4-4 | Docs: nota en `API.md` (acuse + property + i18n) + acta de cierre | ⬜ |