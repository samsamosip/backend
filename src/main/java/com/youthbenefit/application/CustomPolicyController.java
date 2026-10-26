package com.youthbenefit.application;

import com.youthbenefit.auth.CurrentOwner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공고 직접 등록", description = "WF07 1차: 손으로 입력한 공고를 준비함에 담기")
@RestController
@RequestMapping("/api/v1/me/custom-policies")
@RequiredArgsConstructor
public class CustomPolicyController {

	private final CustomPolicyService service;

	@Operation(summary = "공고 직접 등록",
			description = "나만 보이는 공고를 만들고 준비함에 바로 담는다. 응답은 준비함 상세(source = USER). 공고명만 필수. "
					+ "applyEndDate 없으면 상시, applyEndTime(\"18:00\")은 마감일이 있을 때만. category: 일자리·주거·교육·복지문화·참여권리. "
					+ "documents 는 서류 이름 목록 → 체크리스트. 판정은 없고 D-day·마감 임박 알림은 된다")
	@PostMapping
	public ResponseEntity<ApplicationResponses.Detail> create(@CurrentOwner UUID owner,
			@Valid @RequestBody ApplicationRequests.Custom request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(owner, request));
	}

	@Operation(summary = "직접 등록 공고 수정", description = "보낸 값 전체로 바꾼다(안 보낸 칸은 비워짐). 서류 목록을 바꾸면 체크리스트도 맞춘다 — 남은 서류의 체크는 유지")
	@PutMapping("/{policyId}")
	public ApplicationResponses.Detail update(@CurrentOwner UUID owner, @PathVariable Long policyId,
			@Valid @RequestBody ApplicationRequests.Custom request) {
		return service.update(owner, policyId, request);
	}

	@Operation(summary = "직접 등록 공고 삭제", description = "공고와 준비함·알림을 지운다. 준비함에서 빼도(DELETE /me/applications/{id}) 같이 지워진다")
	@DeleteMapping("/{policyId}")
	public ResponseEntity<Void> delete(@CurrentOwner UUID owner, @PathVariable Long policyId) {
		service.delete(owner, policyId);
		return ResponseEntity.noContent().build();
	}

}
