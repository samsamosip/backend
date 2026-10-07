package com.youthbenefit.common;

import java.util.Map;

/** 400 응답 형식. errors 는 항목 이름 → 이유 (항목과 상관없는 오류면 비어 있다). */
public record ErrorResponse(String message, Map<String, String> errors) {
}
