package com.youthbenefit.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림함", description = "WF07 준비함에 담은 공고가 바뀌면 알림")
@RestController
@RequiredArgsConstructor
public class NotificationController {

	private static final String OWNER = "X-Anonymous-Id";

	private final NotificationService service;

	@Operation(summary = "알림 목록", description = "최신순. unreadCount 는 안 읽은 알림 전체 개수(배지용), summary 는 한 줄 요약")
	@GetMapping("/api/v1/me/notifications")
	public NotificationResponses.Page list(@RequestHeader(OWNER) UUID owner,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(owner, Math.max(page, 0), Math.clamp(size, 1, 100));
	}

	@Operation(summary = "알림 상세", description = "바뀐 항목 전/후(changes). 열면 읽음으로 표시된다")
	@GetMapping("/api/v1/me/notifications/{id}")
	public NotificationResponses.Detail open(@RequestHeader(OWNER) UUID owner, @PathVariable Long id) {
		return service.open(owner, id);
	}

	@Operation(summary = "모두 읽음")
	@PostMapping("/api/v1/me/notifications/read-all")
	public Map<String, Integer> readAll(@RequestHeader(OWNER) UUID owner) {
		return Map.of("updated", service.readAll(owner));
	}

	@Operation(summary = "알림 삭제")
	@DeleteMapping("/api/v1/me/notifications/{id}")
	public ResponseEntity<Void> delete(@RequestHeader(OWNER) UUID owner, @PathVariable Long id) {
		service.delete(owner, id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "공고 변경 이력", description = "이 공고가 언제 무엇이 바뀌었는지 (최신순)")
	@GetMapping("/api/v1/policies/{id}/changes")
	public List<NotificationResponses.History> history(@PathVariable Long id) {
		return service.history(id);
	}

}
