package com.youthbenefit.common;

import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/** 오류는 모두 같은 모양(ErrorResponse)으로 돌려준다. 프론트는 message 를 그대로 보여주면 된다. */
@RestControllerAdvice
public class ApiExceptionHandler {

	/** 404 등 컨트롤러가 직접 던진 상태 코드. Spring 기본 응답은 메시지를 숨겨서 여기서 꺼내 준다. */
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ErrorResponse> status(ResponseStatusException ex) {
		String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
		return ResponseEntity.status(ex.getStatusCode()).body(new ErrorResponse(message, Map.of()));
	}

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
		// 어느 칸이 왜 틀렸는지 알 수 있으면 그 칸 이름으로 알려준다: {"status": "INTERESTED, PREPARING, APPLIED 중 하나여야 합니다"}
		if (ex.getCause() instanceof MismatchedInputException mismatch && field(mismatch) != null) {
			return new ErrorResponse("입력값을 확인해 주세요.", Map.of(field(mismatch), reason(mismatch.getTargetType())));
		}
		return new ErrorResponse("요청 본문을 읽을 수 없습니다. JSON 형식을 확인해 주세요.", Map.of());
	}

	private static String field(JacksonException ex) {
		List<JacksonException.Reference> path = ex.getPath();
		return path.isEmpty() ? null : path.getLast().getPropertyName();
	}

	private static String reason(Class<?> type) {
		if (type == null) {
			return "값의 형식이 맞지 않습니다";
		}
		if (type.isEnum()) {
			return String.join(", ", Arrays.stream(type.getEnumConstants()).map(Object::toString).toList()) + " 중 하나여야 합니다";
		}
		if (Temporal.class.isAssignableFrom(type)) {
			return "날짜는 YYYY-MM-DD 형식이어야 합니다";
		}
		if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != boolean.class) {
			return "숫자여야 합니다";
		}
		if (type == Boolean.class || type == boolean.class) {
			return "true 또는 false 여야 합니다";
		}
		if (List.class.isAssignableFrom(type)) {
			return "목록([...]) 이어야 합니다";
		}
		return "값의 형식이 맞지 않습니다";
	}

	@ExceptionHandler({ MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse header(Exception ex) {
		return new ErrorResponse("X-Anonymous-Id 헤더에 UUID 를 넣어 주세요.", Map.of());
	}

}
