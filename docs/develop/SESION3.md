# Sesión 3 — Rama `develop`: plantillas HTML con Thymeleaf para el email

> Continuación de mejoras sobre `develop`. Backlog y acta en `docs/develop/`.

## Contexto

- Hoy `ContactService.sendNotificationAsync` envía la notificación de contacto en **texto
  plano** vía SMTP (Resend) con `MimeMessageHelper.setText(String)`, async y best-effort.
- Objetivo de la sesión: enviar **HTML con plantillas Thymeleaf** (no texto plano), con
  fallback de texto plano (multipart/alternative) para accesibilidad y entregabilidad.

## Decisiones de diseño

- **Dependencia**: `spring-boot-starter-thymeleaf` (template engine compatible Boot 4).
- **Renderer**: `MailTemplateRenderer` inyecta el `SpringTemplateEngine` autoconfigurado;
  `render(template, variables)` → HTML. Plantillas en `src/main/resources/templates/mail/`.
- **Multipart/alternative**: `MimeMessageHelper.setText(plain, html)` (JavaMail genera
  `multipart/alternative`). El texto plano actual se conserva como alternativa.
- **HTML "de correo"**: estilo **inline** + layout en tablas (~600px); sin `<style>`
  global ni flexbox (no los respetan los clientes). Sin frameworks CSS.
- **Escapado**: `th:text` (auto-escape) en `name`, `email`, `subject`, `body` (datos de
  usuario). Nunca `th:utext` con contenido de usuario.
- **Sin romper el flujo**: best-effort async; render dentro del `try` (si falla la
  plantilla, el submit de contacto sigue funcionando). `replyTo` y asunto `[Contacto] …`
  se mantienen.
- **Idioma**: plantilla en `es` (notificación interna al admin); i18n es/en queda para
  una sesión futura.

## Lista de tareas (backlog de la sesión)

| Pieza | Tarea | Estado |
|---|---|---|
| P3-0 | Este documento (backlog + decisiones) | ⬜ |
| P3-1 | Dep `thymeleaf` + `MailTemplateRenderer` + `templates/mail/contact.html` + helper texto plano + tests (render + escape `<script>`) | ⬜ |
| P3-2 | Cablear `ContactService` con `setText(plain, html)` + test que captura el `MimeMessage` (texto + HTML en multipart) | ⬜ |
| P3-3 | Docs: nota en `API.md` + acta de cierre | ⬜ |