package com.youthbenefit.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인 요청 모양 모음. 비밀번호 규칙은 {@link AuthService#checkPassword}. */
public final class AuthRequests {

	private AuthRequests() {
	}

	public record Signup(@NotBlank(message = "이메일을 입력해 주세요") String email,
			@NotBlank(message = "비밀번호를 입력해 주세요") String password,
			@NotBlank(message = "닉네임을 입력해 주세요") @Size(min = 2, max = 10, message = "닉네임은 2~10자로 써 주세요") String nickname) {
	}

	public record Email(@NotBlank(message = "이메일을 입력해 주세요") String email) {
	}

	public record VerifyCode(@NotBlank(message = "이메일을 입력해 주세요") String email,
			@NotBlank(message = "코드를 입력해 주세요") String code) {
	}

	public record Login(@NotBlank(message = "이메일을 입력해 주세요") String email,
			@NotBlank(message = "비밀번호를 입력해 주세요") String password) {
	}

	/** 프론트가 카카오에서 받은 인가 코드와, 그때 쓴 리다이렉트 주소 */
	public record Kakao(@NotBlank(message = "code 가 필요합니다") String code,
			@NotBlank(message = "redirectUri 가 필요합니다") String redirectUri) {
	}

	public record Refresh(@NotBlank(message = "refreshToken 이 필요합니다") String refreshToken) {
	}

	public record ResetPassword(@NotBlank(message = "resetToken 이 필요합니다") String resetToken,
			@NotBlank(message = "새 비밀번호를 입력해 주세요") String newPassword) {
	}

	public record ChangePassword(@NotBlank(message = "지금 비밀번호를 입력해 주세요") String currentPassword,
			@NotBlank(message = "새 비밀번호를 입력해 주세요") String newPassword) {
	}

	public record Rename(@NotBlank(message = "닉네임을 입력해 주세요") @Size(min = 2, max = 10, message = "닉네임은 2~10자로 써 주세요") String nickname) {
	}

}
