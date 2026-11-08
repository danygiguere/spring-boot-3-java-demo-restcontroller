package com.example.restcontrollerdemo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom field constraint: plain text only. Rejects HTML tags and control characters (newlines and tabs are fine).
 * {@code null} is valid, so combine with {@code @NotBlank} when the field is required.
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

    String message() default "{validation.safeText}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
