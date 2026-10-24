package com.youthbenefit.notification;

import com.youthbenefit.eligibility.ConditionResult;
import com.youthbenefit.eligibility.EligibilityService;
import com.youthbenefit.eligibility.ProfileFacts;
import com.youthbenefit.policy.Policy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * 같은 공고의 이전 값과 새 값을 비교해 화면에 보이는 항목 중 바뀐 것만 돌려준다. 조회수·수정 시각처럼 화면과 상관없는 값은 보지 않는다.
 * 자격 조건은 판정 엔진이 만드는 설명("만 19~34세", "서울 관악구 주민등록")끼리 비교해서, 온통청년·학교 장학 모두 같은 문장으로 나온다.
 */
public final class PolicyDiff {

	private static final Map<String, String> PERIOD_LABELS = Map.of("0057001", "특정 기간", "0057002", "상시 모집",
			"0057003", "마감");

	private PolicyDiff() {
	}

	public static List<FieldChange> diff(Policy before, Policy after) {
		List<FieldChange> out = new ArrayList<>();
		field(out, "title", "공고 제목", before, after, Policy::getTitle);
		field(out, "applyStartDate", "신청 시작일", before, after, p -> date(p.getApplyStartDate()));
		field(out, "applyEndDate", "마감일", before, after, p -> date(p.getApplyEndDate()));
		field(out, "applyPeriodCode", "신청 상태", before, after, p -> PERIOD_LABELS.get(p.getApplyPeriodCode()));
		field(out, "supportContent", "지원 내용", before, after, Policy::getSupportContent);
		field(out, "submissionDocuments", "제출 서류", before, after, Policy::getSubmissionDocuments);
		field(out, "applyMethod", "신청 방법", before, after, Policy::getApplyMethod);
		field(out, "applyUrl", "신청 링크", before, after, Policy::getApplyUrl);
		conditions(out, before, after);
		return out;
	}

	/** 마감으로 바뀌었으면 마감, 마감에서 다시 열렸으면 다시 게시, 마감일이 늦어졌으면 연장, 그 밖은 내용 변경. */
	public static ChangeKind guessKind(Policy before, Policy after) {
		boolean wasClosed = Policy.APPLY_PERIOD_CLOSED.equals(before.getApplyPeriodCode());
		boolean isClosed = Policy.APPLY_PERIOD_CLOSED.equals(after.getApplyPeriodCode());
		if (!wasClosed && isClosed) {
			return ChangeKind.CLOSED;
		}
		if (wasClosed && !isClosed) {
			return ChangeKind.RESTORED;
		}
		if (before.getApplyEndDate() != null && after.getApplyEndDate() != null
				&& after.getApplyEndDate().isAfter(before.getApplyEndDate())) {
			return ChangeKind.EXTENDED;
		}
		return ChangeKind.UPDATED;
	}

	private static void field(List<FieldChange> out, String field, String label, Policy before, Policy after,
			Function<Policy, String> value) {
		String b = blankToNull(value.apply(before));
		String a = blankToNull(value.apply(after));
		if (!Objects.equals(normalize(b), normalize(a))) {
			out.add(new FieldChange(field, label, b, a));
		}
	}

	/** 조건 이름(나이, 지역, 소득 …)별로 요구 사항을 모아 비교한다. 글로 된 조건은 원문을 비교한다. */
	private static void conditions(List<FieldChange> out, Policy before, Policy after) {
		Map<String, String> b = requirements(before);
		Map<String, String> a = requirements(after);
		Set<String> labels = new LinkedHashSet<>(b.keySet());
		labels.addAll(a.keySet());
		for (String label : labels) {
			if (!Objects.equals(normalize(b.get(label)), normalize(a.get(label)))) {
				out.add(new FieldChange("eligibility." + label, "자격 조건 · " + label, b.get(label), a.get(label)));
			}
		}
	}

	private static Map<String, String> requirements(Policy p) {
		Map<String, List<String>> byLabel = new LinkedHashMap<>();
		for (ConditionResult c : EligibilityService.describe(p, ProfileFacts.empty())) {
			// 글로 된 조건의 요구 사항은 "원문 확인 필요"로 같아서 원문(evidence)을 비교한다
			String text = c.requirement() != null && c.requirement().contains("원문 확인") ? c.evidence()
					: c.requirement();
			byLabel.computeIfAbsent(c.label(), k -> new ArrayList<>()).add(text);
		}
		Map<String, String> out = new LinkedHashMap<>();
		byLabel.forEach((label, texts) -> out.put(label, String.join(" / ", texts)));
		return out;
	}

	private static String date(LocalDate d) {
		return d == null ? null : d.toString();
	}

	/**
	 * 비교용으로만 정리한다(저장·표시는 원래 값): 원본이 공백·줄바꿈만 바꾸거나 "&gt;" 와 ">" 를 섞어 써서 생기는 가짜 변경을 막는다(실측).
	 */
	static String normalize(String s) {
		if (s == null) {
			return null;
		}
		return s.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&quot;", "\"")
			.replace("&#39;", "'")
			.replace("&apos;", "'")
			.replace("&amp;", "&")
			.replaceAll("\\s+", " ")
			.strip();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}

}
