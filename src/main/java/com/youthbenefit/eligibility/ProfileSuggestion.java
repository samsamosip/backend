package com.youthbenefit.eligibility;

/**
 * 홈 배너(WF02) 한 줄: 비어 있는 내 정보 하나와 그걸 채우면 생기는 효과.
 *
 * @param field 프로필 항목 이름 (PATCH /api/v1/me/profile 에 그대로 쓴다. 나이 조건은 birthDate)
 * @param label 화면 이름 ("소득")
 * @param resolvable 이 항목만 채우면 결과가 확정되는(확인 필요 → 일치/불일치) 공고 수
 * @param affected 이 항목이 비어서 확인 필요인 공고 수 (다른 조건 때문에 채워도 확인 필요로 남을 수 있음)
 * @param message 배너 문구 ("소득을 입력하면 공고 2개가 더 판정돼요")
 */
public record ProfileSuggestion(String field, String label, int resolvable, int affected, String message) {
}
