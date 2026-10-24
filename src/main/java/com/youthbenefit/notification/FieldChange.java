package com.youthbenefit.notification;

/**
 * 바뀐 항목 하나. field 는 프론트가 구분할 키(applyEndDate, eligibility.나이 …), label 은 화면에 보일 이름.
 * before/after 가 null 이면 그 값이 없었거나 없어진 것이다.
 */
public record FieldChange(String field, String label, String before, String after) {
}
