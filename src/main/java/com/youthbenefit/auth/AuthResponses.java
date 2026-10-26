package com.youthbenefit.auth;

import java.time.LocalDateTime;

/** 로그인 응답 모양 모음. */
public final class AuthResponses {

	private AuthResponses() {
	}

	/**
	 * 로그인 성공. accessToken 은 API 마다 Authorization: Bearer 로 보내고(30분), 만료되면 refreshToken 으로
	 * /api/v1/auth/refresh 를 불러 새로 받는다(14일). 둘 다 *ExpiresIn 은 초.
	 */
	public record Tokens(String accessToken, long accessTokenExpiresIn, String refreshToken, long refreshTokenExpiresIn,
			Account account) {
	}

	/** 내 계정. hasPassword 가 false 면 카카오로만 가입한 계정(비밀번호 변경 없음). */
	public record Account(Long id, String email, String nickname, boolean emailVerified, boolean kakaoLinked,
			boolean hasPassword, LocalDateTime createdAt) {

		static Account of(User u) {
			return new Account(u.getId(), u.getEmail(), u.getNickname(), u.isEmailVerified(), u.getKakaoId() != null,
					u.getPasswordHash() != null, u.getCreatedAt());
		}

	}

	/** 코드를 보냈다. expiresInSeconds 동안 쓸 수 있고, resendAfterSeconds 뒤에 다시 받을 수 있다. */
	public record CodeSent(String email, long expiresInSeconds, long resendAfterSeconds) {
	}

	/** 비밀번호 찾기 코드가 맞았다. 이 통행증으로 expiresInSeconds 안에 새 비밀번호를 정한다. */
	public record ResetTicket(String resetToken, long expiresInSeconds) {
	}

}
