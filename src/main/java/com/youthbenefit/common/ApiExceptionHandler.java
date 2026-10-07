package com.youthbenefit.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** 잘못된 요청은 모두 같은 모양(ErrorResponse)의 400 으로 돌려준다. */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(InvalidRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse invalid(InvalidRequestException ex) {
		return new ErrorResponse("입력값을 확인해 주세요.", ex.errors());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse notValid(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
		return new ErrorResponse("입력값을 확인해 주세요.", errors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse unreadable(HttpMessageNotReadableException ex) {
		return new ErrorResponse("요청 본문을 읽을 수 없습니다. 날짜(YYYY-MM-DD)와 선택지 값을 확인해 주세요.", Map.of());
	}

	@ExceptionHandler({ MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse header(Exception ex) {
		return new ErrorResponse("X-Anonymous-Id 헤더에 UUID 를 넣어 주세요.", Map.of());
	}

}
