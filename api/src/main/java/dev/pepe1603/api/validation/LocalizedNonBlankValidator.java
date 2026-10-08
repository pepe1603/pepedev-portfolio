package dev.pepe1603.api.validation;

import dev.pepe1603.api.util.LocalizedText;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Map;

public class LocalizedNonBlankValidator implements ConstraintValidator<LocalizedNonBlank, Map<String, String>> {

    @Override
    public boolean isValid(Map<String, String> value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        for (String lang : new String[] {LocalizedText.DEFAULT_LANG, "en"}) {
            String text = value.get(lang);
            if (text != null && !text.isBlank()) {
                return true;
            }
        }
        return false;
    }
}