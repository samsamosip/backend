package com.youthbenefit.eligibility;

import java.util.List;

/** 조건 하나의 3값 판정: 참 / 거짓 / 미확인 (조건 JSON 명세 v0.1, 기획안 6쪽). */
public enum Truth {

	TRUE, FALSE, UNKNOWN;

	/** 하나라도 거짓이면 거짓, 모두 참이면 참, 나머지는 미확인. */
	static Truth and(List<Truth> values) {
		if (values.contains(FALSE)) {
			return FALSE;
		}
		return values.stream().allMatch(v -> v == TRUE) ? TRUE : UNKNOWN;
	}

	/** 하나라도 참이면 참, 모두 거짓이면 거짓, 나머지는 미확인. */
	static Truth or(List<Truth> values) {
		if (values.contains(TRUE)) {
			return TRUE;
		}
		return !values.isEmpty() && values.stream().allMatch(v -> v == FALSE) ? FALSE : UNKNOWN;
	}

	Truth not() {
		return switch (this) {
			case TRUE -> FALSE;
			case FALSE -> TRUE;
			case UNKNOWN -> UNKNOWN;
		};
	}

	static Truth of(boolean value) {
		return value ? TRUE : FALSE;
	}

}
