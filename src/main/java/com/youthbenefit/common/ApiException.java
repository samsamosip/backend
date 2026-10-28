package com.youthbenefit.common;

import org.springframework.http.HttpStatus;

/** 상태 코드와 화면용 메시지, (필요하면) 프론트가 구분할 code 를 함께 내려보내는 오류. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final java.util.Map<String, Object> details;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, java.util.Map.of());
	}

	public ApiException(HttpStatus status, String code, String message, java.util.Map<String, Object> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = details;
	}

	public java.util.Map<String, Object> details() {
		return details;
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

}
