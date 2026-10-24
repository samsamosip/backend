package com.youthbenefit.notification;

import com.youthbenefit.eligibility.Verdict;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
	 * 목록의 알림 하나. type 은 CHANGE(공고 변경) / NEW_MATCHES(새 공고) / DEADLINE(마감 임박), label 은 탭·배지에 쓸 이름
	 * (변경이면 "마감 연장"·"정정"처럼 변경 종류). kind 는 변경 알림일 때만. summary 는 한 줄 요약.
	 * 새 공고 묶음은 policyId·policyTitle 이 없다.
	 */
	public record Summary(Long id, NotificationType type, String label, ChangeKind kind, Long policyId,
			String policyTitle, String summary, boolean read, LocalDateTime createdAt) {
	}

	/**
	 * 알림 상세. 종류에 따라 하나만 채운다: changes(공고 변경 전/후), newPolicies(새 맞춤 공고 목록), deadline(마감일·남은 날).
	 */
	public record Detail(Long id, NotificationType type, String label, ChangeKind kind, Long policyId,
			String policyTitle, String summary, List<FieldChange> changes, NewMatches newPolicies, Deadline deadline,
			boolean read, LocalDateTime createdAt) {
	}

	/** 새 맞춤 공고 묶음. total 은 전체 건수, policies 는 앞쪽 최대 20건. */
	public record NewMatches(int total, List<NewPolicy> policies) {
	}

	public record NewPolicy(Long policyId, String title, String organization, String verdict, String verdictLabel) {
	}

	/** 마감 임박. daysLeft 0 = 오늘 마감. */
	public record Deadline(LocalDate applyEndDate, int daysLeft) {
	}

	/**
	 * 알림 수신 설정 (WF05-b). newMatchVerdicts 는 새 공고 중 어떤 판정까지 알릴지(MATCH, NEEDS_CHECK), deadlineDays 는 마감 며칠
	 * 전에 알릴지(0 = 당일). quietStart/quietEnd 는 방해금지 시간 — 지금은 앱 안 알림만 있어서 저장만 한다.
	 */
	public record SettingsBody(boolean newMatches, List<Verdict> newMatchVerdicts, boolean deadline,
			List<Integer> deadlineDays, boolean changes, LocalTime quietStart, LocalTime quietEnd) {
	}

	/** 공고 하나의 변경 이력 한 줄. */
	public record History(Long id, ChangeKind kind, String kindLabel, List<FieldChange> changes,
			LocalDateTime detectedAt) {
	}

}
