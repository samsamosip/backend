package com.youthbenefit.application;

import com.youthbenefit.policy.Policy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.json.JsonMapper;

/**
 * 온통청년 제출 서류 글(sbmsnDcmntCn)을 체크리스트 항목으로 쪼갠다. 원문 형식이 제각각이라(번호 목록, 쉼표 나열,
 * ※ 안내문, "붙임파일 확인") 확실한 서류 이름만 뽑고, 못 뽑으면 빈 목록을 돌려준다. 화면은 원문도 함께 보여준다.
 */
public final class DocumentListParser {

	/** 서류 하나. optional 은 "[선택]", "(해당자만)" 같은 표시가 있는 경우. */
	public record Document(String name, boolean optional) {
	}

	private static final int MAX_NAME = 80;

	/** 줄 앞 번호·기호: ① 1. 1) 가. 나) - ㅇ ○ □ ■ • · ▶ 등 */
	private static final Pattern LEADING_MARK = Pattern.compile(
			"^\\s*(?:[\\u2460-\\u2473\\u2776-\\u277F]|\\d{1,2}[.)]|[가나다라마바사아자차카타파하][.)]|[-ㅇ○□■•·▶◦●*▪▫◆◇►▸✔✓❍❖]|\\(\\d{1,2}\\))\\s*");

	private static final Pattern NUMBERED = Pattern
		.compile("^\\s*(?:[\\u2460-\\u2473\\u2776-\\u277F]|\\d{1,2}[.)]|[가나다라마바사아자차카타파하][.)]|\\(\\d{1,2}\\))");

	/** "가) 일반근로자" 처럼 글자 번호로 시작하는 소제목인지 보려고 따로 둔다 */
	private static final Pattern LETTER_MARK = Pattern.compile("^\\s*[가나다라마바사아자차카타파하][.)]");

	/** 서류 이름은 보통 이런 글자로 끝난다 (신청서, 신분증, 등본, 증빙서류, 통장, 성적표, 여권, 1부 ...) */
	private static final Pattern DOCUMENT_ENDING = Pattern.compile("(서|증|본|류|장|표|권|부|원|록|자료|사진|파일)$");

	/** 서류 목록의 소제목: "신청인 제출 서류", "공통서류", "취창업자 추가서류", "제출 서류 목록" */
	private static final Pattern SECTION_HEADER = Pattern
		.compile("(제출|추가|공통|신청|구비|필요|필수)\\s*서류(\\s*목록)?$|서류\\s*목록$");

	/** 서류가 아니라 설명 문장으로 끝나는 경우 (괄호 설명을 뗀 뒤 검사): "~ 작성 가능", "~ 경우", "담당자 확인필요" */
	private static final Pattern SENTENCE_ENDING = Pattern
		.compile("(작성|가능|불가|인정|지참|업로드|확인|포함|안내|불필요|필요|경우|없음|예정|한함|따름|\\s시|택\\s?\\d)$");

	/** "[필수]", "(공통)" 같은 괄호 머리말, 또는 "필수 :", "해당자에 한함 :" 같은 콜론 머리말 */
	private static final Pattern TAG_PREFIX = Pattern.compile(
			"^\\s*(?:[\\[(]\\s*(필수|선택|공통|해당자[^\\])]*)\\s*[\\])]|(필수|선택|공통|해당자[가-힣 ]{0,8}?)\\s*[:：])\\s*");

	/** "재학증명서, 재직증명서, 사업자등록증 중 택 1" → 마지막 이름에서 "중 택 1" 을 뗀다 */
	private static final Pattern CHOOSE_ONE = Pattern.compile("\\s*중\\s*택\\s*\\d$");

	/** 문장 끝의 "제출"·"첨부"는 떼고 서류 이름만 남긴다: "본인-대리인 관계 증명 서류 제출" */
	private static final Pattern TRAILING_VERB = Pattern.compile("\\s*(제출|첨부)$");

	private static final List<String> NOTE_WORDS = List.of("붙임", "첨부", "자세한", "홈페이지", "참고", "http", "문의",
			"별도", "확인하시기", "바랍니다", "부탁드립니다", "참조", "따름", "따라", "안내", "아래 ", "다음 중");

	/** 한 줄 안에 이어 쓴 ①②③ 앞에서 나눈다 */
	private static final Pattern INLINE_CIRCLED = Pattern.compile("(?<=\\S)\\s*(?=[\\u2460-\\u2473\\u2776-\\u277F])");

	/** 한 줄 안에 이어 쓴 "1) 신청서 2) 계획서 3) …" 앞에서 나눈다 (두 개 이상일 때만) */
	private static final Pattern INLINE_NUMBERED = Pattern.compile("(?<=\\S)\\s+(?=\\d{1,2}\\)\\s*\\S)");

	/** 끝에 붙은 서식 안내: "신청서 1부 [붙임 1]", "동의서(별지 제2호)" — 안내 단어가 있어도 앞은 서류 이름이다 */
	private static final Pattern TRAILING_FORM_TAG = Pattern
		.compile("\\s*[\\[(]\\s*(붙임|별지|별첨|첨부|서식)[^\\])]*[\\])]\\s*$");

	/** "면접 증빙서류 일체(① 또는 ② 중 택1)" 처럼 아래 ①② 중 하나만 내면 되는 줄 */
	private static final Pattern CHOOSE_ONE_HEADER = Pattern.compile("택\\s?1|택일|중\\s*하나");

	/** "(공 통)", "(우선선발)" 같은 줄 앞 괄호 머리말 */
	private static final Pattern PAREN_LABEL = Pattern.compile("^\\(\\s*[^()]{1,8}?\\s*\\)\\s*");

	private DocumentListParser() {
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** 출처가 정리해 준 서류 목록이 있으면 그대로 쓰고(학교 장학), 없으면 제출 서류 글을 쪼갠다(온통청년). */
	public static List<Document> documents(Policy policy) {
		if (policy.getChecklistDocuments() != null) {
			return List.of(JSON.readValue(policy.getChecklistDocuments(), Document[].class));
		}
		return parse(policy.getSubmissionDocuments());
	}

	public static List<Document> parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		Map<String, Document> out = new LinkedHashMap<>();
		// "▪️", "⚠️" 처럼 그림 글자로 쓴 기호는 글자 모양 선택자를 떼고 경고 표시는 지운다
		String text = unescapeHtml(raw).replace("\uFE0F", "").replace("⚠", "");
		boolean sawNumbered = false;
		boolean chooseOneOpen = false;
		for (String rawLine : joinWrappedLines(text.split("\\R"))) {
			String stripped = rawLine.strip();
			// 안내문 안의 ①② 는 서류 번호가 아니다: "※ 파일을 묶어서 제출(①,②한글파일, ③PDF파일)"
			if (isNote(stripped)) {
				continue;
			}
			// "1. 최종학력증명서" 아래 "▪ 가장 마지막 입학 학교를 …" 는 그 서류의 설명이다
			if (sawNumbered && stripped.startsWith("▪")) {
				continue;
			}
			sawNumbered = sawNumbered || NUMBERED.matcher(stripped).find();
			// "택1" 줄 바로 아래 ①② 서류는 둘 중 하나만 내면 되니 선택으로
			boolean alternative = chooseOneOpen && startsWithCircled(stripped);
			chooseOneOpen = alternative || CHOOSE_ONE_HEADER.matcher(stripped).find() && !startsWithCircled(stripped);
			for (String piece : splitInlineCircled(rawLine)) {
				List<String> numbered = INLINE_NUMBERED.splitAsStream(piece).toList();
				for (String one : numbered.size() >= 2 ? numbered : List.of(piece)) {
					addLine(out, one, alternative);
				}
			}
		}
		return new ArrayList<>(out.values());
	}

	/** 원문에 HTML 기호가 그대로 들어 있는 경우가 있다: &apos;25~26 지방세 ... */
	private static String unescapeHtml(String raw) {
		return raw.replace("&lt;", "<")
			.replace("&gt;", ">")
			.replace("&apos;", "'")
			.replace("&#39;", "'")
			.replace("&quot;", "\"")
			.replace("&amp;", "&");
	}

	/**
	 * ①②③ 을 한 줄에 이어 쓴 목록이 중간에서 줄바꿈된 경우 다음 줄을 앞 줄에 붙인다: "⑧주민등록\n초본(최근 5년)" → "⑧주민등록초본(최근 5년)".
	 * 다음 줄이 번호·기호로 시작하면 새 항목이라 붙이지 않는다.
	 */
	private static List<String> joinWrappedLines(String[] lines) {
		List<String> joined = new ArrayList<>();
		for (String line : lines) {
			if (!joined.isEmpty() && inlineCircledList(joined.getLast()) && !line.isBlank() && !startsWithMark(line)) {
				joined.set(joined.size() - 1, joined.getLast().stripTrailing() + line.strip());
			}
			else {
				joined.add(line);
			}
		}
		return joined;
	}

	/** ※ ☞ * → 는 안내문, <...> 는 "<민원인이 제출해야 하는 서류>" 같은 소제목 */
	private static boolean isNote(String line) {
		return line.startsWith("※") || line.startsWith("☞") || line.startsWith("*") || line.startsWith("→")
				|| line.startsWith("<") || line.startsWith("〈");
	}

	/** 괄호 밖에서 ①②③ 앞을 나눈다. "면접 증빙서류 일체(① 또는 ② 중 택1)" 처럼 괄호 안의 번호는 그대로 둔다 */
	private static List<String> splitInlineCircled(String line) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == '(' || c == '[' || c == '（') {
				depth++;
			}
			else if ((c == ')' || c == ']' || c == '）') && depth > 0) {
				depth--;
			}
			else if (depth == 0 && (c >= 0x2460 && c <= 0x2473 || c >= 0x2776 && c <= 0x277F)
					&& !line.substring(start, i).isBlank()) {
				parts.add(line.substring(start, i));
				start = i;
			}
		}
		parts.add(line.substring(start));
		return parts;
	}

	private static boolean startsWithCircled(String line) {
		return !line.isEmpty() && (line.charAt(0) >= 0x2460 && line.charAt(0) <= 0x2473
				|| line.charAt(0) >= 0x2776 && line.charAt(0) <= 0x277F);
	}

	private static boolean inlineCircledList(String line) {
		return line.codePoints().filter(c -> c >= 0x2460 && c <= 0x2473 || c >= 0x2776 && c <= 0x277F).count() >= 2;
	}

	private static boolean startsWithMark(String line) {
		String stripped = line.strip();
		return LEADING_MARK.matcher(stripped).lookingAt() || stripped.startsWith("※") || stripped.startsWith("☞")
				|| stripped.startsWith("→") || stripped.startsWith("<") || stripped.startsWith("(")
				|| stripped.startsWith("[");
	}

	private static void addLine(Map<String, Document> out, String rawLine, boolean alternative) {
		{
			String line = rawLine.strip();
			if (line.isEmpty() || isNote(line)) {
				return;
			}
			line = PAREN_LABEL.matcher(line).replaceFirst("");
			boolean numbered = NUMBERED.matcher(line).find();
			boolean bulletHeader = line.startsWith("○") || line.startsWith("■") || line.startsWith("❍");
			boolean letterHeader = LETTER_MARK.matcher(line).find();
			line = LEADING_MARK.matcher(line).replaceFirst("");
			// "○ 공통", "○ 근로자", "○ 선정 후(치료비 신청 시)" 같은 소제목
			String lineCore = withoutTrailingParentheses(line);
			if (bulletHeader && lineCore.length() <= 6 && !line.contains(",") && !DOCUMENT_ENDING.matcher(lineCore).find()) {
				return;
			}
			// "가) 일반근로자", "나) 사업자" 같은 소제목 (서류 이름처럼 끝나지 않는 짧은 줄)
			if (letterHeader && line.length() <= 8 && !DOCUMENT_ENDING.matcher(withoutTrailingParentheses(line)).find()) {
				return;
			}
			// 번호 붙은 줄은 한 줄이 서류 하나다. " / " 뒤는 발급처·서식 안내("국세 완납증명서 1부 / 홈텍스, 세무서")라 버리고,
			// 띄어 쓰지 않은 "/" 는 "발생일/신고일" 같은 설명 안에 있어서 나누지 않는다
			List<String> segments = numbered ? List.of(beforeSpacedSlash(line)) : splitOutsideParentheses(line, '/');
			for (String segment : segments) {
				addSegment(out, segment, numbered, alternative);
			}
		}
	}

	private static void addSegment(Map<String, Document> out, String segment, boolean numbered, boolean alternative) {
		String text = segment.strip();
		boolean optional = alternative;
		var tag = TAG_PREFIX.matcher(text);
		if (tag.find() && tag.end() < text.length()) {
			optional = isOptionalTag(tag.group(1) != null ? tag.group(1) : tag.group(2));
			text = text.substring(tag.end()).strip();
		}
		// "필수 제출서류: 신분증, 면접증빙서류" — 콜론 앞이 머리말이면 뒤쪽을 목록으로 쓴다
		int colon = indexOutsideParentheses(text, ':');
		if (colon > 0 && colon < text.length() - 1) {
			String head = text.substring(0, colon).strip();
			if (head.endsWith("서류") || head.endsWith("제출") || head.endsWith("필수") || head.endsWith("선택")) {
				optional = optional || isOptionalTag(head);
				text = text.substring(colon + 1).strip();
				numbered = false;
			}
		}
		List<String> parts = numbered ? List.of(text) : joinShortWords(splitOutsideParentheses(text, ','));
		for (String part : parts) {
			String name = cleanName(part);
			if (name == null) {
				continue;
			}
			boolean itemOptional = optional || isOptionalTag(name);
			out.putIfAbsent(name, new Document(name, itemOptional));
		}
	}

	/** "국세, 지방세 완납증명서" 처럼 짧은 낱말은 뒤 이름과 한 서류다: "국세" 를 따로 떼지 않는다 */
	private static List<String> joinShortWords(List<String> parts) {
		List<String> out = new ArrayList<>();
		String carry = null;
		for (String part : parts) {
			String p = part.strip();
			if (carry != null) {
				p = carry + ", " + p;
				carry = null;
			}
			if (p.length() <= 3 && !DOCUMENT_ENDING.matcher(p).find()) {
				carry = p;
				continue;
			}
			out.add(p);
		}
		if (carry != null) {
			out.add(carry);
		}
		return out;
	}

	private static boolean isOptionalTag(String text) {
		return text.contains("선택") || text.contains("해당자") || text.contains("해당 시") || text.contains("해당시");
	}

	private static String cleanName(String part) {
		String name = part.strip();
		// "※ ..." 와 "*공고일 이후 발급분만 인정" 같은 각주는 떼어 낸다. 괄호 안 "*" 는 각주가 아니다: "증명사진(3*4)"
		int note = firstIndexOf(name.indexOf('※'), indexOutsideParentheses(name, '*'));
		if (note >= 0) {
			name = name.substring(0, note).strip();
		}
		// "주민등록표 초본: 신청월 발급본" → 콜론 뒤는 설명. 괄호 안 콜론("(취창업자:본인)")은 그대로 둔다
		int colon = indexOutsideParentheses(name, ':');
		if (colon > 0) {
			name = name.substring(0, colon).strip();
			// "분량 : A4 3페이지", "과제 1 : 기획안 작성" 처럼 콜론 앞이 서류 이름이 아닌 짧은 머리말
			if (name.length() <= 6 && !DOCUMENT_ENDING.matcher(name).find()) {
				return null;
			}
		}
		name = name.replaceAll("\\s*등\\s*\\d+\\s*종$", "").replaceAll("^(?:및|또는)\\s+", "").strip();
		// 서식 안내는 이름에 남기되("신청서 1부(서식 1)"), 안내 단어 검사에서는 뺀다
		String lower = TRAILING_FORM_TAG.matcher(name).replaceFirst("").toLowerCase();
		if (NOTE_WORDS.stream().anyMatch(lower::contains)) {
			return null;
		}
		name = CHOOSE_ONE.matcher(name).replaceFirst("");
		name = TRAILING_VERB.matcher(name).replaceFirst("");
		if (name.isEmpty() || name.equals("-") || name.length() < 2 || name.length() > MAX_NAME) {
			return null;
		}
		String core = withoutTrailingParentheses(name);
		if (core.isEmpty() || SECTION_HEADER.matcher(core).find() || SENTENCE_ENDING.matcher(core).find()) {
			return null;
		}
		// "~ 인정 됩니다." 처럼 문장으로 끝나면 안내문이다
		if (core.endsWith("니다") || core.endsWith("니다.") || core.endsWith("다.")) {
			return null;
		}
		// "접수", "조건", "중요" 처럼 서류 이름으로 끝나지 않는 짧은 낱말 조각
		if (core.length() <= 3 && !DOCUMENT_ENDING.matcher(core).find()) {
			return null;
		}
		return name;
	}

	/** 끝에 붙은 괄호 설명을 뗀다: "신분증 (방문신청시 필수지참)" → "신분증". 괄호만 있는 이름은 그대로 */
	private static String withoutTrailingParentheses(String name) {
		String core = name.strip();
		while (core.endsWith(")") || core.endsWith("]")) {
			char close = core.charAt(core.length() - 1);
			char open = close == ')' ? '(' : '[';
			int depth = 0;
			int start = -1;
			for (int i = core.length() - 1; i >= 0; i--) {
				char ch = core.charAt(i);
				if (ch == close) {
					depth++;
				}
				else if (ch == open && --depth == 0) {
					start = i;
					break;
				}
			}
			if (start <= 0) {
				break;
			}
			core = core.substring(0, start).strip();
		}
		return core;
	}

	private static String beforeSpacedSlash(String line) {
		int depth = 0;
		for (int i = 1; i < line.length() - 1; i++) {
			char c = line.charAt(i);
			if (c == '(' || c == '[') {
				depth++;
			}
			else if ((c == ')' || c == ']') && depth > 0) {
				depth--;
			}
			else if (c == '/' && depth == 0 && Character.isWhitespace(line.charAt(i - 1))
					&& Character.isWhitespace(line.charAt(i + 1))) {
				return line.substring(0, i);
			}
		}
		return line;
	}

	private static int firstIndexOf(int a, int b) {
		return a < 0 ? b : b < 0 ? a : Math.min(a, b);
	}

	/** 괄호 밖에 있는 첫 번째 ch 의 위치, 없으면 -1 */
	static int indexOutsideParentheses(String text, char ch) {
		int depth = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '(' || c == '[' || c == '（') {
				depth++;
			}
			else if ((c == ')' || c == ']' || c == '）') && depth > 0) {
				depth--;
			}
			else if (c == ch && depth == 0) {
				return i;
			}
		}
		return -1;
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
