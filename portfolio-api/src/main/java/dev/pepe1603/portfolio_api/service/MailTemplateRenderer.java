package dev.pepe1603.portfolio_api.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class MailTemplateRenderer {

    private final SpringTemplateEngine templateEngine;

    public MailTemplateRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String renderContactHtml(Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process("mail/contact", context);
    }
}