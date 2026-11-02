package com.youthbenefit.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자", description = "수집·변환 실행")
@RestController
@RequiredArgsConstructor
public class UsageStatsController {

	private final UsageStatsService service;

	@Operation(summary = "사용 통계 (발표·사용자 평가용)",
			description = "visitors: 내 정보가 필요한 API(맞춤·프로필·준비함·검색 등)를 부른 사람 — 오늘·최근 7일·전체·이틀 이상 다시 온 사람(returning). "
					+ "signups 가입(이메일·카카오), profiles 프로필 입력, applications 준비함, searches·linkImports·captureImports 횟수, "
					+ "daily 최근 14일. 방문·횟수는 countingSince(11/2 배포) 부터 쌓인다. 날짜는 한국 날짜")
	@GetMapping("/api/v1/admin/stats")
	public UsageStatsService.Stats stats() {
		return service.stats();
	}

}
