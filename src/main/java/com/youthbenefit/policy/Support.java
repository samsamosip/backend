package com.youthbenefit.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공고 카드의 지원 금액과 한 줄 요약. label 은 화면에 그대로 쓰는 글("월 최대 30만원", "학기당 180만원")이고, 금액을 모르면 null —
 * 그때는 summary("창업 교육·컨설팅")를 보여 준다.
 *
 * <ul>
 * <li>학교 장학: Harvester 가 정리해 준 금액(benefits)</li>
 * <li>온통청년: 지원 내용 글에서 AI 가 뽑고 코드가 검사한 결과(policies.ai_support), 글이 바뀌었으면 안 씀</li>
 * <li>둘 다 없으면 지원 내용 첫 줄</li>
 * </ul>
 *
 * @param period MONTH 월, YEAR 연, SEMESTER 학기, ONCE 1회, TOTAL 총, 모르면 null
 * @param kind CASH 현금, LOAN 대출, INTEREST 이자 지원, VOUCHER 바우처·현물, SERVICE 서비스·교육(돈 아님), OTHER
 * @param source SCHOOL(Harvester), AI, TEXT(첫 줄)
 * @param months 매월 받는 지원의 최대 기간(개월) — 지원 내용 글의 "최대 12개월", "24개월간", "2년간". 모르면 null
 */
public record Support(String label, Long amountWon, String period, boolean upTo, String kind, String summary,
		String source, Integer months) {

	/** 프롬프트를 바꾸면 올린다 — 해시가 달라져서 다시 뽑는다 */
	public static final String PROMPT_VERSION = "s1";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	public static Support of(Policy p) {
		if (p.getSource() == PolicySource.SCHOOL) {
			Support school = school(p);
			if (school != null) {
				return school;
			}
		}
		if (p.getAiSupport() != null && hash(p).equals(p.getAiSupportHash())) {
			JsonNode ai = JSON.readTree(p.getAiSupport());
			Long amount = ai.path("amountWon").isNumber() ? ai.path("amountWon").asLong() : null;
			String period = ai.path("period").isString() ? ai.path("period").asString() : null;
			boolean upTo = ai.path("upTo").asBoolean(false);
			String kind = ai.path("kind").asString("OTHER");
			String summary = ai.path("summary").isString() ? ai.path("summary").asString() : firstLine(p);
			Integer months = "MONTH".equals(period) ? months(p.getSupportContent()) : null;
			String label = amount == null ? null
					: label(amount, period, upTo, kind) + (months == null ? "" : " · 최대 " + months + "개월");
			return new Support(label, amount, period, upTo, kind, summary, "AI", months);
		}
		return new Support(null, null, null, false, null, firstLine(p), "TEXT", null);
	}

	/** 뽑을 때 쓴 글(제목·지원 내용·설명) + 프롬프트 버전 해시 */
	public static String hash(Policy p) {
		String text = String.join("\u0000", PROMPT_VERSION, String.valueOf(p.getTitle()),
				String.valueOf(p.getSupportContent()), String.valueOf(p.getDescription()));
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** "대출 연 최대 200만원", "2억원 한도 이자 지원", "월 최대 30만원", "학기당 180만원", "20만원 상당" */
	public static String label(long amount, String period, boolean upTo, String kind) {
		// 이자 지원 금액은 대개 대출 원금 한도라 "이자 최대 2억원"이면 이자를 2억 주는 것처럼 읽힌다
		if ("INTEREST".equals(kind)) {
			return won(amount) + " 한도 이자 지원";
		}
		StringBuilder b = new StringBuilder();
		if ("LOAN".equals(kind)) {
			b.append("대출 ");
		}
		b.append(switch (period == null ? "" : period) {
			case "MONTH" -> "월 ";
			case "YEAR" -> "연 ";
			case "SEMESTER" -> "학기당 ";
			case "TOTAL" -> "총 ";
			default -> "";
		});
		if (upTo) {
			b.append("최대 ");
		}
		b.append(won(amount));
		return "VOUCHER".equals(kind) ? b.append(" 상당").toString() : b.toString();
	}

	/** 30만원, 1,000만원, 2억원, 1억 5,000만원, 5,000원 */
	public static String won(long amount) {
		NumberFormat f = NumberFormat.getNumberInstance(Locale.KOREA);
		long eok = amount / 100_000_000;
		long rest = amount % 100_000_000;
		if (eok > 0) {
			return rest == 0 ? f.format(eok) + "억원"
					: f.format(eok) + "억 " + (rest % 10_000 == 0 ? f.format(rest / 10_000) + "만원" : f.format(rest) + "원");
		}
		return amount % 10_000 == 0 ? f.format(amount / 10_000) + "만원" : f.format(amount) + "원";
	}

	private static final Pattern MONEY = Pattern.compile(
			"(?:([0-9][0-9,.]*)억)?(?:([0-9][0-9,.]*)천만)?(?:([0-9][0-9,.]*)백만)?(?:([0-9][0-9,.]*)만)?(?:([0-9][0-9,.]*)천)?([0-9][0-9,]*)?원");

	/** 글에 나온 금액들(원): "월 30만원" → 300000, "5백만원" → 5000000, "1.8백만원" → 1800000, "40,000천원" → 40000000 */
	public static List<Long> amounts(String text) {
		List<Long> out = new ArrayList<>();
		if (text == null) {
			return out;
		}
		Matcher m = MONEY.matcher(text.replaceAll("\\s+", ""));
		long[] units = { 100_000_000L, 10_000_000L, 1_000_000L, 10_000L, 1_000L, 1L };
		while (m.find()) {
			double total = 0;
			boolean any = false;
			for (int i = 0; i < units.length; i++) {
				String g = m.group(i + 1);
				if (g != null && !g.isEmpty()) {
					try {
						total += Double.parseDouble(g.replace(",", "")) * units[i];
						any = true;
					}
					catch (NumberFormatException ex) {
						any = false;
						break;
					}
				}
			}
			if (any && total > 0) {
				out.add(Math.round(total));
			}
		}
		return out;
	}

	/** Harvester benefits: 가장 큰 금액, 지급 주기, 범위면 "최대". 비율(등록금 50%)이면 그 글 */
	private static Support school(Policy p) {
		if (p.getRawPayload() == null) {
			return null;
		}
		JsonNode benefits = JSON.readTree(p.getRawPayload()).path("benefits");
		long best = 0;
		String period = null;
		boolean upTo = false;
		String percent = null;
		for (JsonNode b : benefits) {
			long max = Math.round(b.path("amount_max").asDouble(0));
			if (max > best) {
				best = max;
				period = switch (b.path("payment_frequency").asString("")) {
					case "per_semester" -> "SEMESTER";
					case "monthly", "per_month" -> "MONTH";
					case "yearly", "per_year", "annual" -> "YEAR";
					case "one_time", "once", "lump_sum" -> "ONCE";
					default -> null;
				};
				upTo = Math.round(b.path("amount_min").asDouble(0)) != max;
			}
			if (b.path("percentage").isNumber() && percent == null) {
				int pct = b.path("percentage").asInt();
				boolean tuition = "tuition".equals(b.path("benefit_kind").asString(""))
						|| b.path("raw_text").asString("").contains("등록금");
				percent = tuition ? (pct >= 100 ? "등록금 전액" : "등록금 " + pct + "%") : pct + "%";
			}
		}
		if (best > 0) {
			// 학자금 대출도 장학 목록에 섞여 온다
			String kind = p.getTitle() != null && p.getTitle().contains("대출") ? "LOAN" : "CASH";
			return new Support(label(best, period, upTo || benefits.size() > 1, kind), best, period,
					upTo || benefits.size() > 1, kind, firstLine(p), "SCHOOL", null);
		}
		return percent == null ? null : new Support(percent, null, null, false, "CASH", firstLine(p), "SCHOOL", null);
	}

	private static final Pattern MONTHS = Pattern.compile("(?:최대\\s*)?([0-9]{1,2})\\s*개월");

	private static final Pattern YEARS = Pattern.compile("([0-9])\\s*년\\s*(?:간|동안)");

	/** 글에 나온 지원 기간 중 가장 긴 것(개월): "최대 12개월", "24개월간", "2년간" → 24. 1~60개월만 */
	static Integer months(String text) {
		if (text == null) {
			return null;
		}
		int best = 0;
		Matcher m = MONTHS.matcher(text);
		while (m.find()) {
			best = Math.max(best, Integer.parseInt(m.group(1)));
		}
		Matcher y = YEARS.matcher(text);
		while (y.find()) {
			best = Math.max(best, Integer.parseInt(y.group(1)) * 12);
		}
		return best >= 1 && best <= 60 ? best : null;
	}

	/** 지원 내용 첫 줄(머리 기호·괄호 머리말 뺌), 30자 */
	static String firstLine(Policy p) {
		String text = p.getSupportContent() != null ? p.getSupportContent() : p.getDescription();
		if (text == null) {
			return null;
		}
		for (String line : text.split("\\R")) {
			// 머리 기호와 "1." "2)" 같은 번호만 뺀다 (연도 "2026"은 남김)
			String t = line.replaceAll("^[\\s\\-·•▸ㅇ○□■◆①-⑳]*", "")
				.replaceAll("^[0-9]{1,2}[.)]\\s*", "")
				.replaceAll("^\\([^)]{1,8}\\)\\s*", "")
				.strip();
			if (t.length() >= 2) {
				return t.length() <= 30 ? t : t.substring(0, 30) + "…";
			}
		}
		return null;
	}

}
