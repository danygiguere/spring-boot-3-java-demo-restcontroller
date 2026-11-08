package com.example.restcontrollerdemo.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class NoDuplicatesValidator implements ConstraintValidator<NoDuplicates, Collection<?>> {

    @Override
    public boolean isValid(Collection<?> values, ConstraintValidatorContext context) {
        if (values == null) {
            return true;
        }
        Set<Object> seen = new HashSet<>();
        return values.stream().allMatch(seen::add);
    }
}
