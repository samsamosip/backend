package com.youthbenefit.importer;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.youthbenefit.policy.Categories;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import tools.jackson.databind.JsonNode;

/**
 * 불러온 결과 = 공고 직접 등록(POST /api/v1/me/custom-policies) 입력칸 그대로. 프론트는 이 값을 입력칸에 채워 보여 주고, 사용자가
 * 확인·수정한 뒤 등록한다. 규칙에 안 맞는 칸(날짜 형식 오류, 목록 밖 분류 등)은 null — 추측해서 채우지 않는다.
 */
public record ImportPrefill(String title, String organization, LocalDate applyEndDate,
		@JsonFormat(pattern = "HH:mm") LocalTime applyEndTime,
		String category, String memo, String applyUrl) {

	/** 공고 직접 등록 입력칸 길이 한도 ({@code ApplicationRequests.Custom}) */
	private static final int TITLE_MAX = 100;

	private static final int ORGANIZATION_MAX = 100;

	private static final int MEMO_MAX = 2000;

	private static final int URL_MAX = 1000;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	/** 불러오기 서버 결과(result)를 입력칸 규칙에 맞춘다. sourceUrl 이 없거나 이상하면 사용자가 준 링크를 쓴다. */
	public static ImportPrefill from(JsonNode result, String requestedUrl) {
		LocalDate date = date(text(result, "applyEndDate"));
		LocalTime time = date == null ? null : time(text(result, "applyEndTime"));
		String category = text(result, "category");
		String url = webLink(text(result, "sourceUrl"));
		return new ImportPrefill(cut(text(result, "title"), TITLE_MAX), cut(text(result, "organization"), ORGANIZATION_MAX),
				date, time, category != null && Categories.OFFICIAL.contains(category) ? category : null,
				cut(text(result, "memo"), MEMO_MAX), url != null ? url : webLink(requestedUrl));
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node == null ? null : node.get(field);
		if (value == null || !value.isString()) {
			return null;
		}
		String s = value.asString().strip();
		return s.isEmpty() ? null : s;
	}

	private static LocalDate date(String s) {
		if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}")) {
			return null;
		}
		try {
			return LocalDate.parse(s);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	private static LocalTime time(String s) {
		if (s == null || !s.matches("\\d{2}:\\d{2}")) {
			return null;
		}
		try {
			return LocalTime.parse(s, TIME);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	/** 입력칸보다 길면 자른다 (그대로 두면 등록할 때 길이 오류) */
	private static String cut(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max).strip();
	}

	static String webLink(String url) {
		if (url == null || url.length() > URL_MAX) {
			return null;
		}
		try {
			URI uri = URI.create(url);
			boolean web = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
			return web && uri.getHost() != null ? url : null;
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
