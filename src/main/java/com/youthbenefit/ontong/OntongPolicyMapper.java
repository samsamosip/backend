package com.youthbenefit.ontong;

import com.youthbenefit.policy.Categories;
import com.youthbenefit.policy.PolicyContent;
import com.youthbenefit.policy.Regions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 온통청년 정책 한 건(원본 Map)을 {@link PolicyContent}로 바꾼다. 필드 이름과 코드값은 바꾸지 않고, 실측으로 확인한 형식 문제만
 * 정리한다: 빈 값이 공백 문자열("        ")로 오는 것, 신청 기간이 "20260101 ~ 20261231" 문자열로 오는 것.
 */
@Component
public class OntongPolicyMapper {

	/** 내용과 상관없이 자주 바뀌는 필드. 변경 감지 해시에서 뺀다 (조회수가 오르면 매일 "수정됨"으로 세던 문제). */
	static final Set<String> VOLATILE_FIELDS = Set.of("inqCnt");

	private static final Pattern YMD = Pattern.compile("(\\d{8})");

	private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

	private static final DateTimeFormatter MODIFIED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final JsonMapper jsonMapper;

	public OntongPolicyMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public String sourceId(Map<String, Object> item) {
		return text(item, "plcyNo");
	}

	public PolicyContent toContent(Map<String, Object> item) {
		String rawPayload = jsonMapper.writeValueAsString(item);
		String applyPeriodRaw = text(item, "aplyYmd");
		List<LocalDate> applyDates = parseDates(applyPeriodRaw);
		List<String> sidoCodes = Regions.sidoCodes(text(item, "zipCd"));
		List<String> categories = Categories.normalize(text(item, "lclsfNm"));
		return new PolicyContent(
				text(item, "plcyNm"),
				text(item, "plcyExplnCn"),
				text(item, "plcySprtCn"),
				text(item, "lclsfNm"),
				text(item, "mclsfNm"),
				text(item, "plcyKywdNm"),
				text(item, "sprvsnInstCdNm"),
				text(item, "aplyPrdSeCd"),
				applyPeriodRaw,
				applyDates.isEmpty() ? null : applyDates.getFirst(),
				applyDates.isEmpty() ? null : applyDates.getLast(),
				integer(item, "sprtTrgtMinAge"),
				integer(item, "sprtTrgtMaxAge"),
				text(item, "sprtTrgtAgeLmtYn"),
				text(item, "zipCd"),
				text(item, "earnCndSeCd"),
				integer(item, "earnMinAmt"),
				integer(item, "earnMaxAmt"),
				text(item, "earnEtcCn"),
				text(item, "jobCd"),
				text(item, "schoolCd"),
				text(item, "mrgSttsCd"),
				text(item, "plcyMajorCd"),
				text(item, "sbizCd"),
				text(item, "addAplyQlfcCndCn"),
				text(item, "ptcpPrpTrgtCn"),
				text(item, "aplyUrlAddr"),
				text(item, "refUrlAddr1"),
				text(item, "refUrlAddr2"),
				text(item, "plcyAplyMthdCn"),
				text(item, "srngMthdCn"),
				text(item, "sbmsnDcmntCn"),
				dateTime(text(item, "lastMdfcnDt")),
				sha256(jsonMapper.writeValueAsString(withoutVolatileFields(item))),
				rawPayload,
				sidoCodes.isEmpty() ? null : String.join(",", sidoCodes),
				Regions.isNationwide(sidoCodes),
				categories.isEmpty() ? null : String.join(",", categories));
	}

	private static Map<String, Object> withoutVolatileFields(Map<String, Object> item) {
		Map<String, Object> copy = new LinkedHashMap<>(item);
		VOLATILE_FIELDS.forEach(copy::remove);
		return copy;
	}

	/** 공백만 있는 값은 null 로 본다. */
	static String text(Map<String, Object> item, String key) {
		Object value = item.get(key);
		if (value == null) {
			return null;
		}
		String trimmed = value.toString().strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** 숫자가 아닌 값(실측 4건)은 null. 0은 "제한 없음"이라는 뜻이라 그대로 둔다. */
	static Integer integer(Map<String, Object> item, String key) {
		String value = text(item, key);
		if (value == null) {
			return null;
		}
		try {
			return Integer.valueOf(value);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	/** "20260101 ~ 20261231" 또는 여러 구간("…\N20260301 ~ 20260331")에서 날짜를 순서대로 뽑는다. */
	static List<LocalDate> parseDates(String raw) {
		if (raw == null) {
			return List.of();
		}
		Matcher matcher = YMD.matcher(raw);
		List<LocalDate> dates = new java.util.ArrayList<>();
		while (matcher.find()) {
			try {
				dates.add(LocalDate.parse(matcher.group(1), BASIC_DATE));
			}
			catch (DateTimeParseException ignored) {
				// 형식이 틀린 날짜는 건너뛴다
			}
		}
		return dates;
	}

	static LocalDateTime dateTime(String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return LocalDateTime.parse(raw, MODIFIED_AT);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
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
