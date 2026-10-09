package com.youthbenefit.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 온통청년 제출 서류 글(sbmsnDcmntCn)을 체크리스트 항목으로 쪼갠다. 원문 형식이 제각각이라(번호 목록, 쉼표 나열,
 * ※ 안내문, "붙임파일 확인") 확실한 서류 이름만 뽑고, 못 뽑으면 빈 목록을 돌려준다. 화면은 원문도 함께 보여준다.
 */
public final class DocumentListParser {

	/** 서류 하나. optional 은 "[선택]", "(해당자만)" 같은 표시가 있는 경우. */
	public record Document(String name, boolean optional) {
	}

	private static final int MAX_NAME = 80;

	/** 줄 앞 번호·기호: ① 1. 1) - ㅇ ○ □ ■ • · ▶ 등 */
	private static final Pattern LEADING_MARK = Pattern
		.compile("^\\s*(?:[\\u2460-\\u2473\\u2776-\\u277F]|\\d{1,2}[.)]|[-ㅇ○□■•·▶◦●*]|\\(\\d{1,2}\\))\\s*");

	private static final Pattern NUMBERED = Pattern.compile("^\\s*(?:[\\u2460-\\u2473\\u2776-\\u277F]|\\d{1,2}[.)]|\\(\\d{1,2}\\))");

	/** "[필수]", "(공통)" 같은 괄호 머리말, 또는 "필수 :", "해당자에 한함 :" 같은 콜론 머리말 */
	private static final Pattern TAG_PREFIX = Pattern.compile(
			"^\\s*(?:[\\[(]\\s*(필수|선택|공통|해당자[^\\])]*)\\s*[\\])]|(필수|선택|공통|해당자[가-힣 ]{0,8}?)\\s*[:：])\\s*");

	private static final List<String> NOTE_WORDS = List.of("붙임", "첨부", "자세한", "홈페이지", "참고", "http", "문의",
			"별도", "공고문", "확인하시기", "바랍니다", "부탁드립니다", "참조", "따름", "따라", "안내");

	/** 한 줄 안에 이어 쓴 ①②③ 앞에서 나눈다 */
	private static final Pattern INLINE_CIRCLED = Pattern.compile("(?<=\\S)\\s*(?=[\\u2460-\\u2473\\u2776-\\u277F])");

	/** "(공 통)", "(우선선발)" 같은 줄 앞 괄호 머리말 */
	private static final Pattern PAREN_LABEL = Pattern.compile("^\\(\\s*[^()]{1,8}?\\s*\\)\\s*");

	private DocumentListParser() {
	}

	public static List<Document> parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		Map<String, Document> out = new LinkedHashMap<>();
		for (String rawLine : raw.split("\\R")) {
			for (String piece : INLINE_CIRCLED.split(rawLine)) {
				addLine(out, piece);
			}
		}
		return new ArrayList<>(out.values());
	}

	private static void addLine(Map<String, Document> out, String rawLine) {
		{
			String line = rawLine.strip();
			if (line.isEmpty() || line.startsWith("※") || line.startsWith("☞") || line.startsWith("*")) {
				return;
			}
			line = PAREN_LABEL.matcher(line).replaceFirst("");
			boolean numbered = NUMBERED.matcher(line).find();
			boolean bulletHeader = line.startsWith("○") || line.startsWith("■");
			line = LEADING_MARK.matcher(line).replaceFirst("");
			// "○ 공통", "○ 근로자" 같은 소제목
			if (bulletHeader && line.length() <= 6 && !line.contains(",")) {
				return;
			}
			for (String segment : splitOutsideParentheses(line, '/')) {
				addSegment(out, segment, numbered);
			}
		}
	}

	private static void addSegment(Map<String, Document> out, String segment, boolean numbered) {
		String text = segment.strip();
		boolean optional = false;
		var tag = TAG_PREFIX.matcher(text);
		if (tag.find() && tag.end() < text.length()) {
			optional = isOptionalTag(tag.group(1) != null ? tag.group(1) : tag.group(2));
			text = text.substring(tag.end()).strip();
		}
		// "필수 제출서류: 신분증, 면접증빙서류" — 콜론 앞이 머리말이면 뒤쪽을 목록으로 쓴다
		int colon = text.indexOf(':');
		if (colon > 0 && colon < text.length() - 1) {
			String head = text.substring(0, colon).strip();
			if (head.endsWith("서류") || head.endsWith("제출") || head.endsWith("필수") || head.endsWith("선택")) {
				optional = optional || isOptionalTag(head);
				text = text.substring(colon + 1).strip();
				numbered = false;
			}
		}
		List<String> parts = numbered ? List.of(text) : splitOutsideParentheses(text, ',');
		for (String part : parts) {
			String name = cleanName(part);
			if (name == null) {
				continue;
			}
			boolean itemOptional = optional || isOptionalTag(name);
			out.putIfAbsent(name, new Document(name, itemOptional));
		}
	}

	private static boolean isOptionalTag(String text) {
		return text.contains("선택") || text.contains("해당자") || text.contains("해당 시") || text.contains("해당시");
	}

	private static String cleanName(String part) {
		String name = part.strip();
		int note = name.indexOf('※');
		if (note >= 0) {
			name = name.substring(0, note).strip();
		}
		int colon = name.indexOf(':');
		if (colon > 0) {
			name = name.substring(0, colon).strip();
		}
		name = name.replaceAll("\\s*등\\s*\\d+\\s*종$", "").replaceAll("^(?:및|또는)\\s+", "").strip();
		if (name.isEmpty() || name.equals("-") || name.length() < 2 || name.length() > MAX_NAME) {
			return null;
		}
		String lower = name.toLowerCase();
		if (NOTE_WORDS.stream().anyMatch(lower::contains)) {
			return null;
		}
		return name;
	}

	/** 괄호 밖의 구분자에서만 나눈다: "거주지 증빙(초본, 재학증명서), 통장사본" → 2개. ',' 는 '，' '、' 도 포함 */
	static List<String> splitOutsideParentheses(String text, char delimiter) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		StringBuilder current = new StringBuilder();
		for (char ch : text.toCharArray()) {
			if (ch == '(' || ch == '[' || ch == '（') {
				depth++;
			}
			else if ((ch == ')' || ch == ']' || ch == '）') && depth > 0) {
				depth--;
			}
			boolean isDelimiter = delimiter == ',' ? (ch == ',' || ch == '，' || ch == '、') : ch == delimiter;
			if (isDelimiter && depth == 0) {
				parts.add(current.toString());
				current.setLength(0);
			}
			else {
				current.append(ch);
			}
		}
		parts.add(current.toString());
		return parts;
	}

}
