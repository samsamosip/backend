package com.youthbenefit.policy;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 온통청년 원문에 HTML 기호가 그대로 들어 있는 경우가 있다("&apos;공인&apos;자격증", "교육&middot;컨설팅"). 화면에 내보낼 때만 글자로 되돌린다.
 * 저장된 원문은 그대로 둔다 — AI 변환 결과가 원문 해시로 묶여 있어서 원문을 바꾸면 다시 변환해야 한다.
 */
public final class HtmlText {

	private static final Pattern ENTITY = Pattern.compile("&(#\\d{1,6}|#x[0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});");

	private static final Map<String, String> NAMED = Map.ofEntries(Map.entry("amp", "&"), Map.entry("lt", "<"),
			Map.entry("gt", ">"), Map.entry("quot", "\""), Map.entry("apos", "'"), Map.entry("nbsp", " "),
			Map.entry("middot", "·"), Map.entry("bull", "•"), Map.entry("rarr", "→"), Map.entry("larr", "←"),
			Map.entry("lsquo", "‘"), Map.entry("rsquo", "’"), Map.entry("ldquo", "“"), Map.entry("rdquo", "”"),
			Map.entry("hellip", "…"), Map.entry("ndash", "–"), Map.entry("mdash", "—"), Map.entry("times", "×"),
			Map.entry("sim", "∼"), Map.entry("deg", "°"));

	private HtmlText() {
	}

	/** 아는 기호만 바꾸고 모르는 것("&foo;")은 그대로 둔다. null 은 null. */
	public static String unescape(String s) {
		if (s == null || s.indexOf('&') < 0) {
			return s;
		}
		Matcher m = ENTITY.matcher(s);
		StringBuilder out = new StringBuilder();
		while (m.find()) {
			String name = m.group(1);
			String replacement = null;
			if (name.startsWith("#x") || name.startsWith("#X")) {
				replacement = codePoint(Integer.parseInt(name.substring(2), 16));
			}
			else if (name.startsWith("#")) {
				replacement = codePoint(Integer.parseInt(name.substring(1)));
			}
			else {
				replacement = NAMED.get(name);
			}
			m.appendReplacement(out, Matcher.quoteReplacement(replacement != null ? replacement : m.group()));
		}
		m.appendTail(out);
		return out.toString();
	}

	private static String codePoint(int cp) {
		return Character.isValidCodePoint(cp) && cp != 0 ? new String(Character.toChars(cp)) : null;
	}

}
