package dev.pepe1603.portfolio_api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = LocalizedNonBlankValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface LocalizedNonBlank {

    String message() default "El texto bilingüe debe incluir al menos un valor en 'es' o 'en'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}