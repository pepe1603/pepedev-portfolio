package dev.pepe1603.portfolio_api.service;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class MailTemplateRenderer {

    private static final Locale ADMIN_LOCALE = Locale.forLanguageTag("es");

    private final SpringTemplateEngine templateEngine;

    public MailTemplateRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String renderContactHtml(Map<String, Object> variables, Locale locale) {
        return render("mail/contact", variables, locale);
    }

    public String renderContactHtml(Map<String, Object> variables) {
        return renderContactHtml(variables, ADMIN_LOCALE);
    }

    public String renderAckHtml(Map<String, Object> variables, Locale locale) {
        return render("mail/ack", variables, locale);
    }

    public String renderResetHtml(Map<String, Object> variables, Locale locale) {
        return render("mail/reset", variables, locale);
    }

    public String renderOtpHtml(Map<String, Object> variables, Locale locale) {
        return render("mail/otp", variables, locale);
    }

    private String render(String template, Map<String, Object> variables, Locale locale) {
        Context context = new Context(locale);
        context.setVariables(variables);
        return templateEngine.process(template, context);
    }
}