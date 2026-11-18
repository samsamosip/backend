package com.youthbenefit.eligibility;

import com.youthbenefit.eligibility.Condition.Check;
import com.youthbenefit.eligibility.Condition.Cmp;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "중위소득 150% 이하", "기준 중위소득 120% 이하 가구", "건강보험료 기준 중위소득 140% 이하"처럼 가구 기준중위소득 기준 하나만 적힌 글을
 * 판정 규칙(householdMedianIncomePct 이하)으로 바꾼다. 범위("50% 초과 ~ 150% 이하"), 가구 유형별 기준, 재산 조건처럼 다른 말이 섞이면
 * 바꾸지 않고 원문 확인으로 남긴다. 화면은 가구원 수·가구 월 소득으로 계산한 %를 저장한다.
 */
final class MedianIncomeText {

	/** 띄어쓰기를 지운 글 전체가 이 모양일 때만 */
	private static final Pattern SIMPLE = Pattern.compile("^(?:\\(?소득(?:기준)?\\)?[:：]?)?(?:'?\\d{2,4}년(?:도)?)?"
			+ "(?:청년)?(?:가구(?:당)?)?(?:소득)?(?:건강보험료(?:부과액|소득판정)?(?:기준)?)?(?:가구)?(?:소득)?(?:기준)?"
			+ "중위소득(\\d{2,3})%(?:이하|이내)(?:인)?(?:가구|자|청년)?\\.?$");

	private MedianIncomeText() {
	}

	/** 규칙으로 바꿀 수 있으면 그 비교, 아니면 null */
	static Check read(String text) {
		if (text == null) {
			return null;
		}
		Matcher m = SIMPLE.matcher(text.replaceAll("\\s+", ""));
		if (!m.matches()) {
			return null;
		}
		return new Check("householdMedianIncomePct", Cmp.LTE, List.of(m.group(1)), "원문: " + text.strip());
	}

}
