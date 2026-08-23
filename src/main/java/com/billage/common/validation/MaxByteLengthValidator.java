package com.billage.common.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxByteLengthValidator implements ConstraintValidator<MaxByteLength, String> {

	private int max;

	@Override
	public void initialize(MaxByteLength constraint) {
		this.max = constraint.value();
	}

	/** null 은 통과시킨다 — 필수 여부는 {@code @NotBlank} 등이 판단한다. */
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
	}
}
