package com.youthbenefit.eligibility;

import com.youthbenefit.policy.PolicySummaryResponse;

/** 맞춤 홈(WF02) 카드 하나: 공고 요약 + 판정 + 한 줄 이유 + 마감까지 남은 날(오늘 마감 0, 상시 모집 null). */
public record MatchItem(PolicySummaryResponse policy, Verdict verdict, String verdictLabel, String summary,
		Integer dDay) {
}
