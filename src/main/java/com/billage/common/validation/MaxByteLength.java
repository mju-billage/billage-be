package com.billage.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * 문자열의 <b>UTF-8 바이트 길이</b> 상한을 검증한다.
 *
 * <p>{@code @Size} 는 글자 수를 세므로 바이트 단위 제약(BCrypt 72바이트, DB VARCHAR 등)에는 쓸 수 없다.
 * 한글은 글자당 3바이트라 글자 수로만 막으면 검증을 통과한 뒤 더 아래 계층에서 터진다.
 */
@Documented
@Constraint(validatedBy = MaxByteLengthValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxByteLength {

	String message() default "입력값이 허용된 길이를 초과했습니다.";

	int value();

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
