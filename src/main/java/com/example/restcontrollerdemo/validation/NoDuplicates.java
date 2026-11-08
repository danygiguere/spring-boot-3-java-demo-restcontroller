package com.example.restcontrollerdemo.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Custom collection constraint: no element may appear twice. {@code null} is valid. */
@Documented
@Constraint(validatedBy = NoDuplicatesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoDuplicates {

    String message() default "{validation.noDuplicates}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
