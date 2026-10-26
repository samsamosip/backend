package com.youthbenefit.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * 오류 응답 형식. errors 는 항목 이름 → 이유 (항목과 상관없는 오류면 비어 있다).
 * code 는 프론트가 화면을 나눠야 하는 오류에만 붙는다 (EMAIL_NOT_VERIFIED, ACCOUNT_LOCKED, TOKEN_EXPIRED 등).
 */
public record ErrorResponse(String message, Map<String, String> errors,
		@JsonInclude(JsonInclude.Include.NON_NULL) String code) {

	public ErrorResponse(String message, Map<String, String> errors) {
		this(message, errors, null);
	}

}
