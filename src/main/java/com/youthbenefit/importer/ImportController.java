package com.youthbenefit.importer;

import com.youthbenefit.auth.CurrentOwner;
import com.youthbenefit.common.ApiException;
import com.youthbenefit.common.RateLimiter;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공고 직접 등록", description = "WF07 1차: 손으로 입력한 공고를 준비함에 담기")
@RestController
public class ImportController {

	private final ImportJobService service;

	/** 한 사람이 1시간에 불러올 수 있는 수. 넘으면 429 */
	private final RateLimiter perOwner;

	public ImportController(ImportJobService service, ImporterProperties properties, Clock clock) {
		this.service = service;
		this.perOwner = new RateLimiter(properties.perHour(), Duration.ofHours(1), clock);
	}

	public record StartRequest(String url) {
	}

	@Operation(summary = "링크 불러오기 시작",
			description = "공고 링크를 주면 불러오기 작업을 만들고 바로 202 로 답한다(status QUEUED). 그다음 GET /api/v1/me/imports/{id} 를 "
					+ "2초마다 불러 SUCCEEDED 면 prefill 을 입력칸에 채워 보여 주고, 사용자가 확인한 뒤 POST /api/v1/me/custom-policies 로 등록한다. "
					+ "FAILED 면 error.message 를 보여 주고 직접 입력으로. 불러오기 서버가 꺼져 있으면 503 IMPORT_UNAVAILABLE, "
					+ "1시간에 20번 넘으면 429 RATE_LIMITED")
	@PostMapping("/api/v1/me/imports")
	public ResponseEntity<ImportJobService.ImportView> start(@CurrentOwner UUID owner,
			@RequestBody StartRequest request) {
		if (!perOwner.tryAcquire(owner.toString())) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "불러오기가 너무 잦아요. 잠시 후 다시 해 주세요.");
		}
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.start(owner, request.url()));
	}

	@Operation(summary = "링크 불러오기 상태",
			description = "status: QUEUED·RUNNING(기다리는 중) → SUCCEEDED(prefill: title·organization·applyEndDate·applyEndTime·"
					+ "category·memo·applyUrl — 공고 직접 등록 입력칸 그대로, 못 찾은 칸은 null) 또는 FAILED(error.code·message). "
					+ "3분 넘게 안 끝나면 FAILED TIMEOUT")
	@GetMapping("/api/v1/me/imports/{id}")
	public ImportJobService.ImportView get(@CurrentOwner UUID owner, @PathVariable UUID id) {
		return service.get(owner, id);
	}

	/** 불러오기 서버 → 백엔드 결과 콜백. 사람용이 아니라 문서에서 숨긴다. 서명 확인 후 200. */
	@Hidden
	@PostMapping("/api/v1/internal/import-jobs/{id}/result")
	public ResponseEntity<Void> callback(@PathVariable UUID id,
			@RequestHeader(name = "X-Timestamp", required = false) String timestamp,
			@RequestHeader(name = "X-Signature", required = false) String signature, HttpServletRequest request)
			throws IOException {
		// 서명은 본문 그대로에 대해 계산하므로 직접 읽는다
		service.callback(id, timestamp, signature, request.getInputStream().readAllBytes());
		return ResponseEntity.ok().build();
	}

}
