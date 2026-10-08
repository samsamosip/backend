package com.youthbenefit.eligibility;

import java.util.List;

/**
 * 공고 하나의 판정 (WF03 공고 상세).
 *
 * @param summary 한 줄 설명 ("소득·추가 자격 조건 확인 필요")
 * @param missingFields 프로필에서 채우면 다시 판정되는 항목
 */
public record PolicyEligibility(Long policyId, Verdict verdict, String verdictLabel, String summary,
		List<ConditionResult> conditions, List<String> missingFields) {
}
