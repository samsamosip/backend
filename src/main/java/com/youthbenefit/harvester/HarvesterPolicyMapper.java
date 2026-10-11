package com.youthbenefit.harvester;

import com.youthbenefit.policy.Duplicates;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyContent;
import com.youthbenefit.policy.Regions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Harvester 장학 공고 상세(JSON)를 {@link PolicyContent}로 바꾼다. 온통청년 전용 칸(나이·zipCd 등)은 비워 두고, 자격 조건은
 * 원본(raw_payload)의 eligibility 를 판정할 때 읽는다(HarvesterConditions). 서류는 정리된 목록을 그대로 체크리스트로 쓴다.
 */
@Component
public class HarvesterPolicyMapper {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 이 상태면 신청이 끝났거나 공고가 사라진 것 */
	static final Set<String> CLOSED_STATUSES = Set.of("closed", "cancelled", "suspended", "source_missing", "removed",
			"archived");

	private static final Set<String> APPLICATION_WINDOWS = Set.of("application", "additional_application");

	private static final Map<String, String> METHOD_LABELS = Map.of("online", "온라인", "visit", "방문", "postal", "우편",
			"email", "이메일");

	private final JsonMapper jsonMapper;

	public HarvesterPolicyMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/**
	 * @param listItem 목록의 요약(version_id 를 쓴다)
	 * @param detailJson 상세 응답 원문
	 */
	public PolicyContent toContent(Map<String, Object> listItem, String detailJson) {
		JsonNode d = jsonMapper.readTree(detailJson);
		String title = abbreviate(text(d, "title"), 300);
		String organization = abbreviate(text(d, "organization"), 255);
		List<LocalDate> starts = new ArrayList<>();
		List<LocalDate> ends = new ArrayList<>();
		List<String> windowTexts = new ArrayList<>();
		for (JsonNode w : d.path("application_windows")) {
			if (APPLICATION_WINDOWS.contains(text(w, "window_kind"))) {
				addDate(starts, text(w, "start_date"));
				addDate(ends, text(w, "end_date"));
				addIfPresent(windowTexts, text(w, "raw_text"));
			}
		}
		LocalDate start = starts.stream().min(LocalDate::compareTo).orElse(null);
		LocalDate end = ends.stream().max(LocalDate::compareTo).orElse(null);
		List<String> sido = sidoCodes(d.path("eligibility"));
		return new PolicyContent(
				title,
				text(d, "description"),
				joinLines(d.path("benefits"), "raw_text"),
				"장학금",
				"학교 장학",
				null,
				organization,
				applyPeriodCode(text(d, "status"), end),
				abbreviate(String.join(" / ", windowTexts), 100),
				start,
				end,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
				firstStatedUrl(d.path("application_methods")),
				sourceUrl(d, 0),
				sourceUrl(d, 1),
				applyMethod(d.path("application_methods")),
				text(d.path("selection"), "process"),
				documentsText(d.path("required_documents")),
				dateTime(text(d, "last_updated_at")),
				null,
				sha256(detailJson),
				detailJson,
				sido.isEmpty() ? null : String.join(",", sido),
				false,
				"교육",
				Duplicates.key(title, organization),
				Objects.toString(listItem.get("version_id"), null),
				documentsJson(d.path("required_documents")));
	}

	/**
	 * 온통청년 신청기간 코드에 맞춘다(목록 조회가 이 코드와 마감일로 거른다). 끝난 상태는 마감, 날짜가 있으면 특정기간, 없으면 상시.
	 * 상태를 모르는데(unknown) 마감일도 없으면 오래된 공고일 수 있어 마감으로 둔다.
	 */
	static String applyPeriodCode(String status, LocalDate end) {
		if (status == null || CLOSED_STATUSES.contains(status)) {
			return Policy.APPLY_PERIOD_CLOSED;
		}
		if (end != null) {
			return "0057001";
		}
		return "unknown".equals(status) ? Policy.APPLY_PERIOD_CLOSED : "0057002";
	}

	/** 거주지 조건이 모두 시도 이름이면 화면 지역 표시에 쓴다. */
	private static List<String> sidoCodes(JsonNode eligibility) {
		if (!"restricted".equals(text(eligibility, "residency_rule_status"))) {
			return List.of();
		}
		List<String> codes = new ArrayList<>();
		for (JsonNode r : eligibility.path("residency_region_codes")) {
			String code = Regions.sidoCodeOf(r.asString());
			if (code == null) {
				return List.of();
			}
			if (!codes.contains(code)) {
				codes.add(code);
			}
		}
		return codes;
	}

	private static String firstStatedUrl(JsonNode methods) {
		for (JsonNode m : methods) {
			if ("stated".equals(text(m.path("url"), "state")) && text(m.path("url"), "value") != null) {
				return abbreviate(text(m.path("url"), "value"), 1000);
			}
		}
		return null;
	}

	private static String sourceUrl(JsonNode d, int index) {
		JsonNode urls = d.path("source_urls");
		return urls.size() > index ? abbreviate(text(urls.get(index), "canonical_url"), 1000) : null;
	}

	/** "방문: 인하대학교 7호관 343호 장학복지팀 — 평일 9시-18시" */
	private static String applyMethod(JsonNode methods) {
		List<String> lines = new ArrayList<>();
		for (JsonNode m : methods) {
			String method = text(m, "method");
			String where = firstNonNull(stated(m, "url"), stated(m, "email"), stated(m, "address"));
			String instructions = text(m, "instructions");
			StringBuilder line = new StringBuilder(METHOD_LABELS.getOrDefault(method, method == null ? "기타" : method));
			if (where != null) {
				line.append(": ").append(where);
			}
			if (instructions != null) {
				line.append(" — ").append(instructions);
			}
			lines.add(line.toString());
		}
		return lines.isEmpty() ? null : String.join("\n", lines);
	}

	private static String stated(JsonNode method, String field) {
		JsonNode f = method.path(field);
		return "stated".equals(text(f, "state")) ? text(f, "value") : null;
	}

	/** 화면에 원문 대신 보여줄 서류 글: "- 장학금 신청서", "- 봉사활동 실적 증빙서류 (해당자: …)" */
	private static String documentsText(JsonNode docs) {
		List<String> lines = new ArrayList<>();
		for (JsonNode doc : docs) {
			String name = text(doc, "name");
			if (name == null) {
				continue;
			}
			String requiredness = text(doc, "requiredness");
			String condition = text(doc, "condition");
			String note = "required".equals(requiredness) ? "" : "optional".equals(requiredness) ? " (선택)"
					: " (해당자" + (condition == null ? "" : ": " + condition) + ")";
			lines.add("- " + name + note);
		}
		return lines.isEmpty() ? null : String.join("\n", lines);
	}

	/** 체크리스트용 [{"name", "optional"}]. 필수가 아닌 것(해당자·선택)은 optional. */
	private String documentsJson(JsonNode docs) {
		List<Map<String, Object>> items = new ArrayList<>();
		for (JsonNode doc : docs) {
			String name = text(doc, "name");
			if (name == null) {
				continue;
			}
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("name", abbreviate(name, 300));
			item.put("optional", !"required".equals(text(doc, "requiredness")));
			items.add(item);
		}
		return items.isEmpty() ? null : jsonMapper.writeValueAsString(items);
	}

	private static String joinLines(JsonNode array, String field) {
		List<String> lines = new ArrayList<>();
		array.forEach(n -> addIfPresent(lines, text(n, field)));
		return lines.isEmpty() ? null : String.join("\n", lines);
	}

	static String text(JsonNode node, String field) {
		JsonNode v = node.path(field);
		if (v.isMissingNode() || v.isNull()) {
			return null;
		}
		String s = v.asString().strip();
		return s.isEmpty() ? null : s;
	}

	private static void addDate(List<LocalDate> dates, String value) {
		if (value == null) {
			return;
		}
		try {
			dates.add(LocalDate.parse(value));
		}
		catch (DateTimeParseException ignored) {
			// 형식이 틀린 날짜는 건너뛴다
		}
	}

	private static void addIfPresent(List<String> list, String value) {
		if (value != null) {
			list.add(value);
		}
	}

	@SafeVarargs
	private static <T> T firstNonNull(T... values) {
		for (T v : values) {
			if (v != null) {
				return v;
			}
		}
		return null;
	}

	static LocalDateTime dateTime(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(raw).atZoneSameInstant(KST).toLocalDateTime();
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static String abbreviate(String value, int max) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max - 1) + "…";
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
