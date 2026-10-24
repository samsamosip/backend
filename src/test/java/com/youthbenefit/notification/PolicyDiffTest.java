package com.youthbenefit.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.youthbenefit.ontong.OntongPolicyMapper;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicySource;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PolicyDiffTest {

	private final OntongPolicyMapper mapper = new OntongPolicyMapper(JsonMapper.builder().build());

	private Policy policy(String... overrides) {
		Map<String, Object> item = new HashMap<>(Map.of("plcyNo", "P1", "plcyNm", "청년 월세 지원", "aplyPrdSeCd", "0057001",
				"aplyYmd", "20261001 ~ 20261030", "plcySprtCn", "월 20만원", "sprtTrgtMinAge", "19", "sprtTrgtMaxAge", "34",
				"inqCnt", "10"));
		for (int i = 0; i < overrides.length; i += 2) {
			item.put(overrides[i], overrides[i + 1]);
		}
		return Policy.create(PolicySource.ONTONG, "P1", mapper.toContent(item));
	}

	@Test
	void reportsVisibleChangesWithBeforeAndAfter() {
		Policy before = policy();
		Policy after = policy("aplyYmd", "20261001 ~ 20261115", "plcySprtCn", "월 30만원", "sprtTrgtMaxAge", "39");

		assertThat(PolicyDiff.diff(before, after)).containsExactly(
				new FieldChange("applyEndDate", "마감일", "2026-10-30", "2026-11-15"),
				new FieldChange("supportContent", "지원 내용", "월 20만원", "월 30만원"),
				new FieldChange("eligibility.나이", "자격 조건 · 나이", "만 19~34세", "만 19~39세"));
		assertThat(PolicyDiff.guessKind(before, after)).isEqualTo(ChangeKind.EXTENDED);
	}

	@Test
	void ignoresViewCountAndDetectsClosing() {
		assertThat(PolicyDiff.diff(policy(), policy("inqCnt", "999"))).isEmpty();

		Policy closed = policy("aplyPrdSeCd", "0057003");
		assertThat(PolicyDiff.diff(policy(), closed))
			.containsExactly(new FieldChange("applyPeriodCode", "신청 상태", "특정 기간", "마감"));
		assertThat(PolicyDiff.guessKind(policy(), closed)).isEqualTo(ChangeKind.CLOSED);
	}

	@Test
	void conditionAddedOrRemovedShowsAsNullSide() {
		Policy withIncome = policy("earnCndSeCd", "0043002", "earnMaxAmt", "5000");

		assertThat(PolicyDiff.diff(policy(), withIncome))
			.containsExactly(new FieldChange("eligibility.소득", "자격 조건 · 소득", null, "연소득 5,000만원 이하"));
	}

	@Test
	void whitespaceAndHtmlEntityDifferencesAreNotChanges() {
		Policy before = policy("plcySprtCn", "기준 변경(2억원 -&gt; 2.5억원)\n  월 20만원 ");
		Policy after = policy("plcySprtCn", "기준 변경(2억원 -> 2.5억원) 월 20만원");

		assertThat(PolicyDiff.diff(before, after)).isEmpty();
	}

	@Test
	void reopenedPolicyIsRestored() {
		assertThat(PolicyDiff.guessKind(policy("aplyPrdSeCd", "0057003"), policy())).isEqualTo(ChangeKind.RESTORED);
	}

}
