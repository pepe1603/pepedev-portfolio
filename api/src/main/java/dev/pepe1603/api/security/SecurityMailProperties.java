package dev.pepe1603.api.security;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Interruptores del correo de seguridad. Los tres son independientes a propósito: el aviso de
 * restablecimiento de contraseña es funcional (sin él, quien pierde la contraseña no puede
 * recuperarla), mientras que los avisos de sesión son ruido que solo interesa si hay varias
 * personas con acceso al panel.
 *
 * <p>Por eso los de sesión arrancan apagados y el de reset arranca activado, que es el
 * comportamiento que ya tenía el proyecto. Ninguno de los tres lanza si falta
 * {@code APP_CONTACT_FROM_EMAIL}: el envío se salta y el aviso correspondiente sale en el log de
 * arranque ({@code StartupSecurityWarnings}).
 */
@Getter
@Component
public class SecurityMailProperties {

    @Value("${APP_MAIL_SECURITY_LOGIN:false}")
    private boolean loginEnabled;

    @Value("${APP_MAIL_SECURITY_LOGOUT:false}")
    private boolean logoutEnabled;

    @Value("${APP_MAIL_SECURITY_RESET:true}")
    private boolean resetEnabled;
}
