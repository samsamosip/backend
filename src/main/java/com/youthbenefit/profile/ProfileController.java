package com.youthbenefit.profile;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 내 프로필 (WF01 온보딩, WF08 내 정보). 로그인이 생기기 전까지는 프론트가 만든 UUID 를
 * X-Anonymous-Id 헤더로 보내 사람을 구분한다(브라우저 localStorage 에 보관). TODO: 로그인으로 교체.
 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class ProfileController {

	static final String ANONYMOUS_ID = "X-Anonymous-Id";

	private final ProfileService profileService;

	@GetMapping("/profile")
	public ProfileResponse get(@RequestHeader(ANONYMOUS_ID) UUID anonymousId) {
		return profileService.find(anonymousId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필이 아직 없습니다"));
	}

	/** 전체 저장. 보내지 않은 항목은 "모름"으로 비워진다. */
	@PutMapping("/profile")
	public ProfileResponse put(@RequestHeader(ANONYMOUS_ID) UUID anonymousId,
			@Valid @RequestBody ProfileRequest request) {
		return profileService.save(anonymousId, request);
	}

	/** 내 데이터 삭제 (WF08). */
	@DeleteMapping
	public ResponseEntity<Void> delete(@RequestHeader(ANONYMOUS_ID) UUID anonymousId) {
		if (!profileService.delete(anonymousId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필이 아직 없습니다");
		}
		return ResponseEntity.noContent().build();
	}

}
