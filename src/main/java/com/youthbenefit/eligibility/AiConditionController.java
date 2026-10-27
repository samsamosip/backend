package com.youthbenefit.eligibility;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자", description = "수집·변환 실행")
@RestController
@RequiredArgsConstructor
public class AiConditionController {

	private final AiConditionService service;

	@Operation(summary = "글 조건 AI 변환 실행",
			description = "신청 가능한 온통청년 공고 중 글 조건(소득 기타·추가 자격·참여 제한)이 아직 변환 안 됐거나 바뀐 것을 limit 개까지 변환한다. "
					+ "결과 {pending, converted, reviewed, failed, tokens}. PwC LLM 사용량이 드니 limit 로 나눠서")
	@PostMapping("/api/v1/admin/ai/conditions")
	public AiConditionService.Result convert(@RequestParam(defaultValue = "50") int limit) {
		return service.convertPending(Math.clamp(limit, 1, 2000));
	}

	@Operation(summary = "글 조건 다시 검사", description = "검사 규칙을 고친 뒤 저장된 AI 답을 다시 검사한다. AI 를 부르지 않아 비용 없음. 결과 {changed}")
	@PostMapping("/api/v1/admin/ai/conditions/recheck")
	public java.util.Map<String, Integer> recheck() {
		return java.util.Map.of("changed", service.recheckAll());
	}

}
