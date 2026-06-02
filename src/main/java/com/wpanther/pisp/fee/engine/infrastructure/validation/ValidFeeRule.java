package com.wpanther.pisp.fee.engine.infrastructure.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = FeeRuleRequestValidator.class)
@Documented
public @interface ValidFeeRule {
    String message() default "Invalid fee rule configuration for the specified fee type";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
