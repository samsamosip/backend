package com.youthbenefit.policy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * 직접 등록 공고에 넣는 값 (검사는 {@code CustomPolicyService}). documents 는 서류 이름 목록 — 체크리스트가 된다.
 *
 * @param category 공식 대분류 5개 중 하나 또는 null
 */
public record CustomPolicy(String title, String organization, LocalDate applyEndDate, LocalTime applyEndTime,
		String category, String memo, String applyUrl, List<String> documents) {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	String documentsText() {
		return documents == null || documents.isEmpty() ? null : String.join("\n", documents);
	}

	/** DocumentListParser 가 읽는 형식: [{"name": "...", "optional": false}] */
	String documentsJson() {
		List<Map<String, Object>> list = documents == null ? List.of()
				: documents.stream().map(d -> Map.<String, Object>of("name", d, "optional", false)).toList();
		return JSON.writeValueAsString(list);
	}

}
