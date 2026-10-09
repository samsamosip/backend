package com.youthbenefit.application;

import com.youthbenefit.policy.Policy;
import java.time.LocalDate;
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
	 * closed 는 마감일이 지났거나 온통청년이 마감(0057003)으로 표시한 경우.
	 */
	public record Deadline(LocalDate applyEndDate, Integer dDay, boolean closed) {

		static Deadline of(Policy p, LocalDate today) {
			Integer dDay = p.getApplyEndDate() == null ? null
					: (int) ChronoUnit.DAYS.between(today, p.getApplyEndDate());
			boolean closed = Policy.APPLY_PERIOD_CLOSED.equals(p.getApplyPeriodCode()) || (dDay != null && dDay < 0);
			return new Deadline(p.getApplyEndDate(), dDay, closed);
		}

	}

	/** 준비함 목록의 카드 하나. */
	public record Summary(Long id, Long policyId, String title, String organization, ApplicationStatus status,
			String statusLabel, Deadline deadline, Progress progress) {

		static Summary of(Application a, LocalDate today) {
			Policy p = a.getPolicy();
			return new Summary(a.getId(), p.getId(), p.getTitle(), p.getOrganization(), a.getStatus(),
					a.getStatus().label(), Deadline.of(p, today), Progress.of(a.getItems()));
		}

	}

	public record Item(Long id, String content, boolean optional, ChecklistItem.Source source, boolean checked) {

		static Item of(ChecklistItem i) {
			return new Item(i.getId(), i.getContent(), i.isOptional(), i.getSource(), i.isChecked());
		}

	}

	/**
	 * 준비함 상세 (WF05). submissionDocumentsRaw 는 공고 원문 — 자동으로 못 뽑은 내용이 있을 수 있어 함께 보여준다.
	 */
	public record Detail(Long id, Long policyId, String title, String organization, ApplicationStatus status,
			String statusLabel, Deadline deadline, Progress progress, List<Item> items, String submissionDocumentsRaw,
			String applyUrl, String applyMethod) {

		static Detail of(Application a, LocalDate today) {
			Policy p = a.getPolicy();
			return new Detail(a.getId(), p.getId(), p.getTitle(), p.getOrganization(), a.getStatus(),
					a.getStatus().label(), Deadline.of(p, today), Progress.of(a.getItems()),
					a.getItems().stream().map(Item::of).toList(), p.getSubmissionDocuments(),
					p.getApplyUrl() != null ? p.getApplyUrl() : p.getReferenceUrl1(), p.getApplyMethod());
		}

	}

}
