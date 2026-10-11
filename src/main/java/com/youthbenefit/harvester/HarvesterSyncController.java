package com.youthbenefit.harvester;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 수동 수집. TODO: 로그인 기능이 생기면 관리자만 호출하도록 막는다. */
@Tag(name = "관리자", description = "온통청년·학교 장학 수집")
@RestController
@RequestMapping("/api/v1/admin/harvester")
@RequiredArgsConstructor
public class HarvesterSyncController {

	private final HarvesterSyncService syncService;

	@Operation(summary = "학교 장학 수집", description = "policy-harvester 에서 장학 공고를 받는다. 처음(또는 full=true)은 목록 전체를 비교해서 상세 요청 한도 때문에 30분 넘게 걸리고, 그다음부터는 변경 목록에서 바뀐 것만 받는다")
	@PostMapping("/sync")
	public HarvesterSyncResult sync(@RequestParam(defaultValue = "false") boolean full) {
		return full ? syncService.syncFull() : syncService.sync();
	}

	@ExceptionHandler(HarvesterApiException.class)
	public ResponseEntity<Map<String, String>> handle(HarvesterApiException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
	}

}
