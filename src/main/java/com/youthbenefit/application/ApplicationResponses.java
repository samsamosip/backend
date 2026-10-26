package com.youthbenefit.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicySource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 준비함 응답 모양 모음. */
public final class ApplicationResponses {

	private ApplicationResponses() {
	}

	/**
	 * 준비 상황. done 은 체크한 항목 수, total 은 전체 항목 수(선택 서류 포함).
	 * requiredDone/requiredTotal 은 필수 서류만 센 값이다.
	 */
	public record Progress(int done, int total, int requiredDone, int requiredTotal) {

		static Progress of(List<ChecklistItem> items) {
			int done = (int) items.stream().filter(ChecklistItem::isChecked).count();
			List<ChecklistItem> required = items.stream().filter(i -> !i.isOptional()).toList();
			int requiredDone = (int) required.stream().filter(ChecklistItem::isChecked).count();
			return new Progress(done, items.size(), requiredDone, required.size());
		}

	}

	/**
	 * 마감. dDay 는 마감까지 남은 날(오늘 마감 0, 지남은 음수), 상시 모집이면 null.
	 * closed 는 마감일이 지났거나 온통청년이 마감(0057003)으로 표시한 경우. applyEndTime 은 직접 등록 공고의 마감 시각(없으면 null).
	 */
	public record Deadline(LocalDate applyEndDate, @JsonFormat(pattern = "HH:mm") LocalTime applyEndTime, Integer dDay, boolean closed) {

		static Deadline of(Policy p, LocalDate today) {
			Integer dDay = p.getApplyEndDate() == null ? null
					: (int) ChronoUnit.DAYS.between(today, p.getApplyEndDate());
			boolean closed = Policy.APPLY_PERIOD_CLOSED.equals(p.getApplyPeriodCode()) || (dDay != null && dDay < 0);
			return new Deadline(p.getApplyEndDate(), p.getApplyEndTime(), dDay, closed);
		}

	}

	/**
	 * 준비함 목록의 카드 하나. appliedAt 은 신청 완료로 표시한 시각("10.15 완료 표시"), 완료가 아니면 null. source 가 USER 면 직접 등록
	 * 공고. documentChanges 는 공고 서류가 바뀌어 표시된 항목 수("서류 변경" 배지).
	 */
	public record Summary(Long id, Long policyId, PolicySource source, String title, String organization,
			ApplicationStatus status, String statusLabel, LocalDateTime appliedAt, Deadline deadline, Progress progress,
			int documentChanges) {

		static Summary of(Application a, LocalDate today) {
			Policy p = a.getPolicy();
			int documentChanges = (int) a.getItems().stream().filter(i -> i.getPolicyChange() != null).count();
			return new Summary(a.getId(), p.getId(), p.getSource(), p.getTitle(), p.getOrganization(), a.getStatus(),
					a.getStatus().label(), a.getAppliedAt(), Deadline.of(p, today), Progress.of(a.getItems()),
					documentChanges);
		}

	}

	/** policyChange: 공고 서류가 바뀌어 붙은 표시(ADDED 새로 추가 / DROPPED 공고에서 빠짐), 없으면 null */
	public record Item(Long id, String content, boolean optional, ChecklistItem.Source source, boolean checked,
			ChecklistItem.PolicyChange policyChange, String policyChangeLabel) {

		static Item of(ChecklistItem i) {
			return new Item(i.getId(), i.getContent(), i.isOptional(), i.getSource(), i.isChecked(), i.getPolicyChange(),
					i.getPolicyChange() == null ? null : i.getPolicyChange().label());
		}

	}

	/**
	 * 준비함 상세 (WF05). submissionDocumentsRaw 는 공고 원문 — 자동으로 못 뽑은 내용이 있을 수 있어 함께 보여준다.
	 */
	public record Detail(Long id, Long policyId, PolicySource source, String title, String organization,
			ApplicationStatus status, String statusLabel, LocalDateTime appliedAt, Deadline deadline, Progress progress,
			List<Item> items, String submissionDocumentsRaw, String applyUrl, String applyMethod, String category,
			String memo) {

		static Detail of(Application a, LocalDate today) {
			Policy p = a.getPolicy();
			return new Detail(a.getId(), p.getId(), p.getSource(), p.getTitle(), p.getOrganization(), a.getStatus(),
					a.getStatus().label(), a.getAppliedAt(), Deadline.of(p, today), Progress.of(a.getItems()),
					a.getItems().stream().map(Item::of).toList(), p.getSubmissionDocuments(),
					p.getApplyUrl() != null ? p.getApplyUrl() : p.getReferenceUrl1(), p.getApplyMethod(),
					p.isCustom() ? p.getCategory() : null, p.getMemo());
		}

	}

}
