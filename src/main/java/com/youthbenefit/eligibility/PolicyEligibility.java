package com.youthbenefit.eligibility;

import java.util.List;

/**
 * 공고 하나의 판정 (WF03 공고 상세).
 *
 * @param summary 한 줄 설명 ("소득·추가 자격 조건 확인 필요")
 * @param missingFields 프로필에서 채우면 다시 판정되는 항목
 */
/**
 * @param summary 카드용 짧은 이유 — 디자인의 "기관 · 소득 정보 확인 필요" 에서 "·" 뒤
 * @param explanation 상세용 한 문장 — 무엇을 하면 되는지 ("소득을 입력하면 바로 확인돼요")
 */
public record PolicyEligibility(Long policyId, Verdict verdict, String verdictLabel, String summary,
		String explanation, List<ConditionResult> conditions, List<String> missingFields) {
}
