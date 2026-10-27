package com.youthbenefit.support;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자", description = "수집·변환 실행")
@RestController
@RequiredArgsConstructor
public class AiSupportController {

	private final AiSupportService service;

	@Operation(summary = "지원 금액 AI 뽑기",
			description = "신청 가능한 온통청년 공고 중 아직 안 뽑았거나 지원 내용이 바뀐 것을 limit 개까지. 결과 {pending, extracted, withAmount, failed, tokens}")
	@PostMapping("/api/v1/admin/ai/support")
	public AiSupportService.Result extract(@RequestParam(defaultValue = "50") int limit) {
		return service.extractPending(Math.clamp(limit, 1, 2000));
	}

	@Operation(summary = "지원 금액 다시 검사", description = "검사 규칙을 고친 뒤 저장된 AI 답을 다시 검사한다. AI 호출·비용 없음")
	@PostMapping("/api/v1/admin/ai/support/recheck")
	public Map<String, Integer> recheck() {
		return Map.of("changed", service.recheckAll());
	}

}
