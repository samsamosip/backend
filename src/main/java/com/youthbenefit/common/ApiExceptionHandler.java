package com.youthbenefit.common;

import java.time.temporal.Temporal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> api(ApiException ex) {
		return ResponseEntity.status(ex.status()).body(new ErrorResponse(ex.getMessage(), Map.of(), ex.code(), ex.details()));
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
		if (ex.getCause() instanceof MismatchedInputException mismatch) {
			return mismatch(mismatch);
		}
		return new ErrorResponse("요청 본문을 읽을 수 없습니다. JSON 형식을 확인해 주세요.", Map.of());
	}

	/** PATCH 처럼 본문을 받은 뒤에 변환하다 형식이 틀린 경우. */
	@ExceptionHandler(MismatchedInputException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse mismatch(MismatchedInputException ex) {
		if (field(ex) != null) {
			return new ErrorResponse("입력값을 확인해 주세요.", Map.of(field(ex), reason(ex.getTargetType())));
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

	/** 올린 파일이 업로드 한도(spring.servlet.multipart)보다 큼 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	@ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
	public ErrorResponse tooLarge(MaxUploadSizeExceededException ex) {
		return new ErrorResponse("캡처는 장당 10MB, 합계 30MB 이하로 올려 주세요.", Map.of());
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse header(MissingRequestHeaderException ex) {
		return new ErrorResponse(ex.getHeaderName() + " 헤더가 필요합니다.", Map.of());
	}

	/** 주소나 검색 조건의 값 형식이 틀림 (예: /policies/abc) */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse typeMismatch(MethodArgumentTypeMismatchException ex) {
		return new ErrorResponse("입력값을 확인해 주세요.", Map.of(ex.getName(), reason(ex.getRequiredType())));
	}

	/** 없는 주소 (예: /api/v1/nope). Spring 기본 영어 응답 대신 같은 모양으로 */
	@ExceptionHandler(NoResourceFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse noResource(NoResourceFoundException ex) {
		return new ErrorResponse("없는 주소예요. API 문서(/docs)에서 주소를 확인해 주세요.", Map.of());
	}

	/** 주소는 맞는데 GET·POST 같은 방식이 틀림 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	@ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
	public ErrorResponse method(HttpRequestMethodNotSupportedException ex) {
		return new ErrorResponse(ex.getMethod() + " 방식은 이 주소에서 쓸 수 없어요.", Map.of());
	}

	/** JSON 이나 파일을 받아야 하는데 다른 형식으로 보냄 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	@ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
	public ErrorResponse mediaType(HttpMediaTypeNotSupportedException ex) {
		return new ErrorResponse("보내는 형식(Content-Type)을 확인해 주세요.", Map.of());
	}

	/** 꼭 필요한 검색 조건이 빠짐 (예: /search 의 q) */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse missingParam(MissingServletRequestParameterException ex) {
		return new ErrorResponse("입력값을 확인해 주세요.", Map.of(ex.getParameterName(), "값이 필요합니다"));
	}

	/** 캡처 올리기에서 파일이 빠짐 */
	@ExceptionHandler(MissingServletRequestPartException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse missingPart(MissingServletRequestPartException ex) {
		return new ErrorResponse("입력값을 확인해 주세요.", Map.of(ex.getRequestPartName(), "파일을 올려 주세요"));
	}

}
