package com.youthbenefit.eligibility;

import java.util.List;

/**
 * 조건 JSON 명세 v0.1 의 노드. 그룹(All/Any/Not)과 비교(Check), 해석 불가(Unparsed)로 이루어진 나무다.
 * 온통청년 공고는 {@link OntongConditions}가 이 나무를 만들고, 나중에 AI 추출 결과도 같은 나무로 바꿔 판정한다.
 */
public sealed interface Condition {

	/** AND: 모두 만족 */
	record All(List<Condition> children) implements Condition {
	}

	/** OR: 하나라도 만족 */
	record Any(List<Condition> children) implements Condition {
	}

	/** NOT: 만족하면 안 됨 (예외 조항) */
	record Not(Condition child) implements Condition {
	}

	/**
	 * 비교 하나. var 는 사용자 변수 이름(명세 2번 표), values 는 기준값.
	 * BETWEEN 은 [최소, 최대], LTE/GTE 는 [기준], IN 은 허용 값 목록, INTERSECTS 는 사용자 값 목록과 겹치는지.
	 */
	record Check(String var, Cmp cmp, List<String> values, String evidence) implements Condition {
	}

	/** 변수로 표현하지 못한 조건. 항상 미확인. label 은 화면에 보일 이름, evidence 는 원문. */
	record Unparsed(String label, String evidence) implements Condition {
	}

	enum Cmp {
		IN, BETWEEN, LTE, GTE, INTERSECTS
	}

}
