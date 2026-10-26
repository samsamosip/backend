package com.youthbenefit.auth;

import com.youthbenefit.common.ApiException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentOwner}, {@link LoginUser} 값을 채운다.
 *
 * <ul>
 * <li>Bearer 토큰이 있으면 그 계정(틀리거나 만료면 401 — 익명으로 넘어가지 않는다)</li>
 * <li>없으면 X-Anonymous-Id. 단, 계정의 UUID 를 헤더로 보내면 401 LOGIN_REQUIRED (UUID 만 알아서는 계정 데이터에 못 들어온다)</li>
 * </ul>
 */
@RequiredArgsConstructor
class OwnerArgumentResolver implements HandlerMethodArgumentResolver {

	static final String ANONYMOUS_HEADER = "X-Anonymous-Id";

	private final TokenService tokenService;

	private final UserRepository users;

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentOwner.class) || parameter.hasParameterAnnotation(LoginUser.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest request, WebDataBinderFactory binderFactory) {
		TokenService.AccessClaims claims = bearer(request);
		if (parameter.hasParameterAnnotation(LoginUser.class)) {
			if (claims == null) {
				throw loginRequired();
			}
			return claims.userId();
		}
		if (claims != null) {
			return claims.ownerId();
		}
		boolean required = parameter.getParameterAnnotation(CurrentOwner.class).required();
		String header = request.getHeader(ANONYMOUS_HEADER);
		if (header == null || header.isBlank()) {
			if (required) {
				throw missingOwner();
			}
			return null;
		}
		UUID anonymousId;
		try {
			anonymousId = UUID.fromString(header.strip());
		}
		catch (IllegalArgumentException ex) {
			throw missingOwner();
		}
		if (users.existsByOwnerId(anonymousId)) {
			throw loginRequired();
		}
		return anonymousId;
	}

	private TokenService.AccessClaims bearer(NativeWebRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return null;
		}
		TokenService.AccessClaims claims = tokenService.verify(authorization.substring(7).strip(), Instant.now());
		if (!users.existsById(claims.userId())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "다시 로그인해 주세요.");
		}
		return claims;
	}

	private static ApiException missingOwner() {
		return new ApiException(HttpStatus.BAD_REQUEST, "OWNER_REQUIRED", "X-Anonymous-Id 헤더에 UUID 를 넣어 주세요.");
	}

	private static ApiException loginRequired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_REQUIRED", "로그인이 필요해요.");
	}

}
