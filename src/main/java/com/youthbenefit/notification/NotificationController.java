package com.youthbenefit.notification;

import com.youthbenefit.auth.CurrentOwner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "알림함", description = "WF07 준비함에 담은 공고가 바뀌면 알림")
@RestController
@RequiredArgsConstructor
public class NotificationController {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final NotificationService service;

	private final DailyNotificationService dailyService;

	@Operation(summary = "알림 목록", description = "최신순. type 으로 한 종류만(CHANGE 공고 변경 / NEW_MATCHES 새 공고 / DEADLINE 마감 임박). "
			+ "tab 은 알림함 탭(디자인 05): NEW 새 공고 / EXTENDED 마감 연장 / CORRECTED 정정(내용 변경 포함) / CANCELLED 취소(공고 내림 포함). "
			+ "unreadCount 는 안 읽은 알림 전체 개수(배지용), label 은 탭·배지 이름(변경이면 마감 연장·정정·취소 등), summary 는 한 줄 요약")
	@GetMapping("/api/v1/me/notifications")
	public NotificationResponses.Page list(@CurrentOwner UUID owner,
			@RequestParam(required = false) NotificationType type, @RequestParam(required = false) NotificationTab tab,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return service.list(owner, type, tab, Math.max(page, 0), Math.clamp(size, 1, 100));
	}

	@Operation(summary = "알림 상세", description = "종류에 따라 changes(공고 변경 전/후), newPolicies(새 맞춤 공고 목록), deadline(마감일·남은 날) 중 하나가 채워진다. 열면 읽음")
	@GetMapping("/api/v1/me/notifications/{id}")
	public NotificationResponses.Detail open(@CurrentOwner UUID owner, @PathVariable Long id) {
		return service.open(owner, id);
	}

	@Operation(summary = "모두 읽음")
	@PostMapping("/api/v1/me/notifications/read-all")
	public Map<String, Integer> readAll(@CurrentOwner UUID owner) {
		return Map.of("updated", service.readAll(owner));
	}

	@Operation(summary = "알림 삭제")
	@DeleteMapping("/api/v1/me/notifications/{id}")
	public ResponseEntity<Void> delete(@CurrentOwner UUID owner, @PathVariable Long id) {
		service.delete(owner, id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "알림 설정 보기", description = "저장한 적이 없으면 전부 켜진 기본값")
	@GetMapping("/api/v1/me/notification-settings")
	public NotificationResponses.SettingsBody settings(@CurrentOwner UUID owner) {
		return service.settings(owner);
	}

	@Operation(summary = "알림 설정 저장", description = "전체 저장. newMatchVerdicts: MATCH·NEEDS_CHECK, deadlineDays: 마감 며칠 전(0 = 당일)")
	@PutMapping("/api/v1/me/notification-settings")
	public NotificationResponses.SettingsBody saveSettings(@CurrentOwner UUID owner,
			@RequestBody NotificationResponses.SettingsBody body) {
		return service.saveSettings(owner, body);
	}

	@Operation(summary = "매일 알림 지금 실행 (관리자)", description = "보통은 매일 아침 8시에 자동으로 돈다. 데모·확인용")
	@PostMapping("/api/v1/admin/notifications/run-daily")
	public DailyNotificationService.Result runDaily() {
		return dailyService.run(LocalDate.now(KST), LocalDateTime.now());
	}

	@Operation(summary = "공고 변경 이력", description = "이 공고가 언제 무엇이 바뀌었는지 (최신순)")
	@GetMapping("/api/v1/policies/{id}/changes")
	public List<NotificationResponses.History> history(@PathVariable Long id) {
		return service.history(id);
	}

}
