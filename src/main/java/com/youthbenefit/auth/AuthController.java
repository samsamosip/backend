package com.youthbenefit.auth;

import com.youthbenefit.auth.AuthResponses.CodeSent;
import com.youthbenefit.auth.AuthResponses.ResetTicket;
import com.youthbenefit.auth.AuthResponses.Tokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인. 로그인·가입 완료 때 X-Anonymous-Id 를 같이 보내면 로그인 전에 쓰던 프로필·준비함·알림을 계정으로 옮긴다. 오류는
 * {"message", "code"} — code 로 화면을 나누면 된다.
 */
@Tag(name = "로그인", description = "이메일 가입·로그인, 카카오 로그인, 비밀번호 찾기")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private static final String ANON_DESC = "로그인 전에 쓰던 브라우저 UUID. 보내면 그 데이터를 계정으로 옮긴다";

	private final AuthService authService;

	@Operation(summary = "이메일 가입 시작", description = "인증 코드(6자리, 10분)를 메일로 보낸다. 비밀번호는 8~64자, 영문+숫자(특수문자 가능). "
			+ "이미 인증된 이메일이면 409 EMAIL_TAKEN, 1분 안에 다시 요청하면 429 CODE_COOLDOWN")
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CodeSent signup(@Valid @RequestBody AuthRequests.Signup request) {
		return authService.signup(request.email(), request.password(), request.nickname());
	}

	@Operation(summary = "가입 인증 코드 다시 받기", description = "1분에 한 번. 429 CODE_COOLDOWN")
	@PostMapping("/email/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CodeSent resend(@Valid @RequestBody AuthRequests.Email request) {
		return authService.resendSignupCode(request.email());
	}

	@Operation(summary = "가입 인증 코드 확인 → 로그인", description = "맞으면 바로 로그인 토큰. 400 CODE_MISMATCH(남은 횟수 안내) / CODE_EXPIRED")
	@PostMapping("/email/verify")
	public Tokens verifyEmail(@Valid @RequestBody AuthRequests.VerifyCode request,
			@Parameter(description = ANON_DESC) @RequestHeader(value = OwnerArgumentResolver.ANONYMOUS_HEADER,
					required = false) UUID anonymousId) {
		return authService.verifySignup(request.email(), request.code(), anonymousId);
	}

	@Operation(summary = "이메일 로그인", description = "401 BAD_CREDENTIALS(남은 횟수 안내), 5번 틀리면 423 ACCOUNT_LOCKED(15분), "
			+ "인증 전이면 403 EMAIL_NOT_VERIFIED(→ /email/resend 로 코드 다시 받기)")
	@PostMapping("/login")
	public Tokens login(@Valid @RequestBody AuthRequests.Login request,
			@Parameter(description = ANON_DESC) @RequestHeader(value = OwnerArgumentResolver.ANONYMOUS_HEADER,
					required = false) UUID anonymousId) {
		return authService.login(request.email(), request.password(), anonymousId);
	}

	@Operation(summary = "카카오 로그인", description = "카카오가 리다이렉트로 준 code 와 그때 쓴 redirectUri 를 보낸다. 처음이면 자동 가입. "
			+ "401 KAKAO_LOGIN_FAILED(코드 만료·재사용 등)")
	@PostMapping("/kakao")
	public Tokens kakao(@Valid @RequestBody AuthRequests.Kakao request,
			@Parameter(description = ANON_DESC) @RequestHeader(value = OwnerArgumentResolver.ANONYMOUS_HEADER,
					required = false) UUID anonymousId) {
		return authService.kakaoLogin(request.code(), request.redirectUri(), anonymousId);
	}

	@Operation(summary = "토큰 갱신", description = "출입증(accessToken, 30분)이 만료되면 갱신 토큰으로 새로 받는다. 갱신 토큰도 새것으로 바뀌니 저장해 둘 것. "
			+ "401 TOKEN_EXPIRED / TOKEN_INVALID / TOKEN_REUSED → 로그인 화면으로")
	@PostMapping("/refresh")
	public Tokens refresh(@Valid @RequestBody AuthRequests.Refresh request) {
		return authService.refresh(request.refreshToken());
	}

	@Operation(summary = "로그아웃", description = "이 기기의 갱신 토큰을 끊는다. 프론트는 토큰을 지우고 새 X-Anonymous-Id 를 만든다")
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@Valid @RequestBody AuthRequests.Refresh request) {
		authService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "비밀번호 찾기 ① 코드 받기", description = "가입 여부와 상관없이 같은 응답(남의 가입 여부를 알 수 없게)")
	@PostMapping("/password/forgot")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public CodeSent forgot(@Valid @RequestBody AuthRequests.Email request) {
		return authService.forgotPassword(request.email());
	}

	@Operation(summary = "비밀번호 찾기 ② 코드 확인", description = "맞으면 resetToken(10분). 다음 화면(새 비밀번호)에서 같이 보낸다")
	@PostMapping("/password/verify-code")
	public ResetTicket verifyResetCode(@Valid @RequestBody AuthRequests.VerifyCode request) {
		return authService.verifyResetCode(request.email(), request.code());
	}

	@Operation(summary = "비밀번호 찾기 ③ 새 비밀번호", description = "바꾸고 바로 로그인. 다른 기기 로그인은 모두 끊긴다. 400 RESET_EXPIRED → 처음부터")
	@PostMapping("/password/reset")
	public Tokens reset(@Valid @RequestBody AuthRequests.ResetPassword request,
			@Parameter(description = ANON_DESC) @RequestHeader(value = OwnerArgumentResolver.ANONYMOUS_HEADER,
					required = false) UUID anonymousId) {
		return authService.resetPassword(request.resetToken(), request.newPassword(), anonymousId);
	}

}
