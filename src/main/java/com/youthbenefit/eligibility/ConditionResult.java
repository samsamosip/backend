package com.youthbenefit.eligibility;

/**
 * 조건 하나의 판정 결과 (WF03 "내 조건과 비교했어요"의 한 줄).
 *
 * @param label 조건 이름 (나이, 지역 …)
 * @param truth 참 / 거짓 / 미확인
 * @param requirement 공고가 요구하는 것 ("만 19~34세")
 * @param myValue 내 값 ("만 23세", 모르면 null)
 * @param missingField 내 값이 비어 있어 미확인이면 그 변수 이름 (프로필에서 채우면 다시 판정된다)
 * @param evidence 근거가 된 원문 또는 출처 필드
 */
public record ConditionResult(String label, Truth truth, String requirement, String myValue, String missingField,
		String evidence) {
}
