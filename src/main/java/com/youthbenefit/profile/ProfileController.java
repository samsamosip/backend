package com.youthbenefit.profile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "내 프로필", description = "WF01 온보딩, WF08 내 정보")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class ProfileController {

	static final String ANONYMOUS_ID = "X-Anonymous-Id";

	private final ProfileService profileService;

	@Operation(summary = "내 프로필 조회", description = "만 나이, 시도 이름, 비어 있는 항목(missingFields) 포함. 없으면 404")
	@GetMapping("/profile")
	public ProfileResponse get(@Parameter(description = "브라우저가 처음 방문 때 만든 UUID (localStorage 보관)", example = "6f1c2b7e-1d1a-4c55-9a6b-0d6a3a1e2f00") @RequestHeader(ANONYMOUS_ID) UUID anonymousId) {
		return profileService.find(anonymousId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필이 아직 없습니다"));
	}

	/** 전체 저장. 보내지 않은 항목은 "모름"으로 비워진다. */
	@Operation(summary = "내 프로필 저장", description = "모든 항목 선택. 보내지 않은 항목은 모름(null)으로 저장. sbizCd 는 null(모름)과 [](해당 없음)을 구분한다. 코드값은 GET /api/v1/codes")
	@PutMapping("/profile")
	public ProfileResponse put(@Parameter(description = "브라우저가 처음 방문 때 만든 UUID (localStorage 보관)", example = "6f1c2b7e-1d1a-4c55-9a6b-0d6a3a1e2f00") @RequestHeader(ANONYMOUS_ID) UUID anonymousId,
			@Valid @RequestBody ProfileRequest request) {
		return profileService.save(anonymousId, request);
	}

	/** 내 데이터 삭제 (WF08). */
	@Operation(summary = "내 데이터 삭제")
	@DeleteMapping
	public ResponseEntity<Void> delete(@Parameter(description = "브라우저가 처음 방문 때 만든 UUID (localStorage 보관)", example = "6f1c2b7e-1d1a-4c55-9a6b-0d6a3a1e2f00") @RequestHeader(ANONYMOUS_ID) UUID anonymousId) {
		if (!profileService.delete(anonymousId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "프로필이 아직 없습니다");
		}
		return ResponseEntity.noContent().build();
	}

}
