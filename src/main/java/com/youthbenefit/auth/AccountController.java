package com.youthbenefit.auth;

import com.youthbenefit.auth.AuthResponses.Account;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 계정 (로그인 필요: Authorization: Bearer 출입증) */
@Tag(name = "내 계정", description = "닉네임, 비밀번호 변경, 회원 탈퇴. 로그인 필요")
@RestController
@RequestMapping("/api/v1/me/account")
@RequiredArgsConstructor
public class AccountController {

	private final AuthService authService;

	@Operation(summary = "내 계정 정보", description = "이메일, 닉네임(\"[이름]님\"), 카카오 연결 여부, 비밀번호 유무")
	@GetMapping
	public Account get(@LoginUser long userId) {
		return authService.account(userId);
	}

	@Operation(summary = "닉네임 바꾸기")
	@PatchMapping
	public Account rename(@LoginUser long userId, @Valid @RequestBody AuthRequests.Rename request) {
		return authService.rename(userId, request.nickname());
	}

	@Operation(summary = "비밀번호 바꾸기", description = "카카오로만 가입한 계정은 400 NO_PASSWORD")
	@PostMapping("/password")
	public ResponseEntity<Void> changePassword(@LoginUser long userId,
			@Valid @RequestBody AuthRequests.ChangePassword request) {
		authService.changePassword(userId, request.currentPassword(), request.newPassword());
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "회원 탈퇴", description = "계정과 프로필·준비함·알림을 모두 지운다")
	@DeleteMapping
	public ResponseEntity<Void> delete(@LoginUser long userId) {
		authService.deleteAccount(userId);
		return ResponseEntity.noContent().build();
	}

}
