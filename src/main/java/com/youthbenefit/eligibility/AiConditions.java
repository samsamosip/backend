package com.youthbenefit.eligibility;

import java.util.List;

/**
 * 글로 된 자격 조건을 AI 로 바꾸고 검사까지 마친 결과 (policies.ai_conditions 에 JSON 으로 저장). requirements 는 판정 엔진의 비교로
 * 그대로 쓰고, unresolved 는 계속 "원문 확인 필요"로 남는다.
 *
 * @param noExtraConditions 글에 실제 조건이 없음("해당없음", "제한없음", 문의 안내 등)
 * @param confidence high / low (low 면 2차 모델이 다시 봤다)
 * @param raw 모델이 낸 JSON 그대로 — 검사 규칙을 고치면 AI 를 다시 부르지 않고 이것으로 다시 검사한다
 */
public record AiConditions(boolean noExtraConditions, List<Requirement> requirements, List<Unresolved> unresolved,
		String confidence, String model, String raw) {

	/** 판정 엔진의 비교 하나 (var 는 ProfileFacts 변수, 지역은 zipCd STARTS_WITH 코드로 바꿔 둔다) */
	public record Requirement(String var, Condition.Cmp cmp, List<String> values, String evidence) {
	}

	/** 판정할 수 없는 조건. exclusion 이면 "해당하면 제외" */
	public record Unresolved(String label, String text, boolean exclusion) {
	}

}
