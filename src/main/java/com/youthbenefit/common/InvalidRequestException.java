package com.youthbenefit.common;

import java.util.Map;

/** 요청 값이 규칙에 맞지 않음. errors 는 항목 이름 → 이유. */
public class InvalidRequestException extends RuntimeException {

	private final Map<String, String> errors;

	public InvalidRequestException(Map<String, String> errors) {
		super("요청 값이 올바르지 않습니다: " + errors);
		this.errors = errors;
	}

	public Map<String, String> errors() {
		return errors;
	}

}
