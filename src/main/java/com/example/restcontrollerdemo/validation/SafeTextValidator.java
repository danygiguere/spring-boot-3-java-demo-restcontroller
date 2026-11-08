package com.example.restcontrollerdemo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class SafeTextValidator implements ConstraintValidator<SafeText, CharSequence> {

    /** An opening or closing tag, a comment or a doctype: {@code <b>}, {@code </p>}, {@code <!--}. */
    private static final Pattern HTML_TAG = Pattern.compile("<[a-zA-Z/!?]");

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return !HTML_TAG.matcher(value).find() && value.chars().noneMatch(SafeTextValidator::isForbiddenControl);
    }

    private static boolean isForbiddenControl(int c) {
        return Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t';
    }
}
