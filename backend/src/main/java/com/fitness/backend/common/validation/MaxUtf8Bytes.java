package com.fitness.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * UTF-8로 셌을 때의 바이트 상한. {@code null}은 통과시킨다(필수 여부는 {@code @NotBlank}가 본다).
 *
 * <p>{@code @Size}는 글자 수를 세는데 BCrypt의 72는 바이트다. 한글은 한 글자가 3바이트라 글자 수로는 통과한
 * 비밀번호가 해시에서 예외를 내 500이 된다.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "너무 깁니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {

        private int max;

        @Override
        public void initialize(MaxUtf8Bytes constraint) {
            this.max = constraint.value();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
