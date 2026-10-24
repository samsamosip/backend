package com.youthbenefit.notification;

import java.time.LocalDateTime;
import java.util.List;

/** 알림함 응답 모양 모음. */
public final class NotificationResponses {

	private NotificationResponses() {
	}

	/** 알림함 목록. unreadCount 는 페이지와 상관없는 전체 안 읽은 개수(배지용). */
	public record Page(long unreadCount, List<Summary> items, int page, int size, long totalElements,
			int totalPages) {
	}

	/**
	 * 목록의 알림 하나. summary 는 한 줄 요약("마감일 2026-10-30 → 2026-11-15", 여러 개면 "외 2건").
	 */
	public record Summary(Long id, Long policyId, String policyTitle, ChangeKind kind, String kindLabel,
			String summary, boolean read, LocalDateTime createdAt) {
	}

	/** 알림 상세: 바뀐 항목 전/후 전부. */
	public record Detail(Long id, Long policyId, String policyTitle, ChangeKind kind, String kindLabel,
			List<FieldChange> changes, boolean read, LocalDateTime createdAt) {
	}

	/** 공고 하나의 변경 이력 한 줄. */
	public record History(Long id, ChangeKind kind, String kindLabel, List<FieldChange> changes,
			LocalDateTime detectedAt) {
	}

}
