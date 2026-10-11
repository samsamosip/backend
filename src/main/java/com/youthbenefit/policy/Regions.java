package com.youthbenefit.policy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 시군구 코드(zipCd) 앞 2자리 = 시도. 실측(2026-10)으로 온통청년에 쓰이는 시도는 16개다 (전남 46·광주 29 → 전남광주 12로 통합).
 * 전국 정책도 "전국"이 아니라 시군구 코드를 전부 나열하므로, 16개 시도를 모두 포함하면 전국으로 본다.
 */
public final class Regions {

	private static final Map<String, String> SIDO_NAMES = new LinkedHashMap<>();

	static {
		SIDO_NAMES.put("11", "서울");
		SIDO_NAMES.put("26", "부산");
		SIDO_NAMES.put("27", "대구");
		SIDO_NAMES.put("28", "인천");
		SIDO_NAMES.put("30", "대전");
		SIDO_NAMES.put("31", "울산");
		SIDO_NAMES.put("36", "세종");
		SIDO_NAMES.put("41", "경기");
		SIDO_NAMES.put("51", "강원");
		SIDO_NAMES.put("43", "충북");
		SIDO_NAMES.put("44", "충남");
		SIDO_NAMES.put("52", "전북");
		SIDO_NAMES.put("12", "전남광주");
		SIDO_NAMES.put("47", "경북");
		SIDO_NAMES.put("48", "경남");
		SIDO_NAMES.put("50", "제주");
	}

	/** 시도 이름(정식·줄임) → 코드. 광주·전남은 통합 코드 12. 학교 장학 거주지 조건("인천", "제주특별자치도")에 쓴다. */
	private static final Map<String, String> SIDO_ALIASES = new LinkedHashMap<>();

	static {
		alias("11", "서울", "서울시", "서울특별시");
		alias("26", "부산", "부산시", "부산광역시");
		alias("27", "대구", "대구시", "대구광역시");
		alias("28", "인천", "인천시", "인천광역시");
		alias("30", "대전", "대전시", "대전광역시");
		alias("31", "울산", "울산시", "울산광역시");
		alias("36", "세종", "세종시", "세종특별자치시");
		alias("41", "경기", "경기도");
		alias("51", "강원", "강원도", "강원특별자치도");
		alias("43", "충북", "충청북도");
		alias("44", "충남", "충청남도");
		alias("52", "전북", "전라북도", "전북특별자치도");
		alias("12", "광주", "광주시", "광주광역시", "전남", "전라남도", "전남광주", "전남광주통합특별시");
		alias("47", "경북", "경상북도");
		alias("48", "경남", "경상남도");
		alias("50", "제주", "제주도", "제주특별자치도");
	}

	private static void alias(String code, String... names) {
		for (String name : names) {
			SIDO_ALIASES.put(name, code);
		}
	}

	/**
	 * 시군구 코드 → 이름. 온통청년 zipCd 에 쓰이는 261개 전부(2026-10 실측). 2026년 새 코드(전남광주 12, 강원 51, 전북 52)는
	 * 옛 코드와 같은 순서이고, 단일 지역 공고 690건의 기관 이름과 대조해 확인했다. 화성시 4개 구(41591~41597)만 데이터에 근거가 없다.
	 */
	private static final Map<String, String> SIGUNGU = new LinkedHashMap<>();

	static {
		sigungu("11110", "종로구");
		sigungu("11140", "중구");
		sigungu("11170", "용산구");
		sigungu("11200", "성동구");
		sigungu("11215", "광진구");
		sigungu("11230", "동대문구");
		sigungu("11260", "중랑구");
		sigungu("11290", "성북구");
		sigungu("11305", "강북구");
		sigungu("11320", "도봉구");
		sigungu("11350", "노원구");
		sigungu("11380", "은평구");
		sigungu("11410", "서대문구");
		sigungu("11440", "마포구");
		sigungu("11470", "양천구");
		sigungu("11500", "강서구");
		sigungu("11530", "구로구");
		sigungu("11545", "금천구");
		sigungu("11560", "영등포구");
		sigungu("11590", "동작구");
		sigungu("11620", "관악구");
		sigungu("11650", "서초구");
		sigungu("11680", "강남구");
		sigungu("11710", "송파구");
		sigungu("11740", "강동구");
		sigungu("26110", "중구");
		sigungu("26140", "서구");
		sigungu("26170", "동구");
		sigungu("26200", "영도구");
		sigungu("26230", "부산진구");
		sigungu("26260", "동래구");
		sigungu("26290", "남구");
		sigungu("26320", "북구");
		sigungu("26350", "해운대구");
		sigungu("26380", "사하구");
		sigungu("26410", "금정구");
		sigungu("26440", "강서구");
		sigungu("26470", "연제구");
		sigungu("26500", "수영구");
		sigungu("26530", "사상구");
		sigungu("26710", "기장군");
		sigungu("27110", "중구");
		sigungu("27140", "동구");
		sigungu("27170", "서구");
		sigungu("27200", "남구");
		sigungu("27230", "북구");
		sigungu("27260", "수성구");
		sigungu("27290", "달서구");
		sigungu("27710", "달성군");
		sigungu("27720", "군위군");
		sigungu("28110", "중구");
		sigungu("28125", "제물포구");
		sigungu("28140", "동구");
		sigungu("28155", "영종구");
		sigungu("28177", "미추홀구");
		sigungu("28185", "연수구");
		sigungu("28200", "남동구");
		sigungu("28237", "부평구");
		sigungu("28245", "계양구");
		sigungu("28260", "서구");
		sigungu("28275", "서해구");
		sigungu("28290", "검단구");
		sigungu("28710", "강화군");
		sigungu("28720", "옹진군");
		sigungu("30110", "동구");
		sigungu("30140", "중구");
		sigungu("30170", "서구");
		sigungu("30200", "유성구");
		sigungu("30230", "대덕구");
		sigungu("31110", "중구");
		sigungu("31140", "남구");
		sigungu("31170", "동구");
		sigungu("31200", "북구");
		sigungu("31710", "울주군");
		sigungu("36110", "세종시");
		sigungu("41111", "수원시 장안구");
		sigungu("41113", "수원시 권선구");
		sigungu("41115", "수원시 팔달구");
		sigungu("41117", "수원시 영통구");
		sigungu("41131", "성남시 수정구");
		sigungu("41133", "성남시 중원구");
		sigungu("41135", "성남시 분당구");
		sigungu("41150", "의정부시");
		sigungu("41171", "안양시 만안구");
		sigungu("41173", "안양시 동안구");
		sigungu("41192", "부천시 원미구");
		sigungu("41194", "부천시 소사구");
		sigungu("41196", "부천시 오정구");
		sigungu("41210", "광명시");
		sigungu("41220", "평택시");
		sigungu("41250", "동두천시");
		sigungu("41271", "안산시 상록구");
		sigungu("41273", "안산시 단원구");
		sigungu("41281", "고양시 덕양구");
		sigungu("41285", "고양시 일산동구");
		sigungu("41287", "고양시 일산서구");
		sigungu("41290", "과천시");
		sigungu("41310", "구리시");
		sigungu("41360", "남양주시");
		sigungu("41370", "오산시");
		sigungu("41390", "시흥시");
		sigungu("41410", "군포시");
		sigungu("41430", "의왕시");
		sigungu("41450", "하남시");
		sigungu("41461", "용인시 처인구");
		sigungu("41463", "용인시 기흥구");
		sigungu("41465", "용인시 수지구");
		sigungu("41480", "파주시");
		sigungu("41500", "이천시");
		sigungu("41550", "안성시");
		sigungu("41570", "김포시");
		sigungu("41590", "화성시");
		sigungu("41591", "화성시 만세구");
		sigungu("41593", "화성시 효행구");
		sigungu("41595", "화성시 병점구");
		sigungu("41597", "화성시 동탄구");
		sigungu("41610", "광주시");
		sigungu("41630", "양주시");
		sigungu("41650", "포천시");
		sigungu("41670", "여주시");
		sigungu("41800", "연천군");
		sigungu("41820", "가평군");
		sigungu("41830", "양평군");
		sigungu("51110", "춘천시");
		sigungu("51130", "원주시");
		sigungu("51150", "강릉시");
		sigungu("51170", "동해시");
		sigungu("51190", "태백시");
		sigungu("51210", "속초시");
		sigungu("51230", "삼척시");
		sigungu("51720", "홍천군");
		sigungu("51730", "횡성군");
		sigungu("51750", "영월군");
		sigungu("51760", "평창군");
		sigungu("51770", "정선군");
		sigungu("51780", "철원군");
		sigungu("51790", "화천군");
		sigungu("51800", "양구군");
		sigungu("51810", "인제군");
		sigungu("51820", "고성군");
		sigungu("51830", "양양군");
		sigungu("43111", "청주시 상당구");
		sigungu("43112", "청주시 서원구");
		sigungu("43113", "청주시 흥덕구");
		sigungu("43114", "청주시 청원구");
		sigungu("43130", "충주시");
		sigungu("43150", "제천시");
		sigungu("43720", "보은군");
		sigungu("43730", "옥천군");
		sigungu("43740", "영동군");
		sigungu("43745", "증평군");
		sigungu("43750", "진천군");
		sigungu("43760", "괴산군");
		sigungu("43770", "음성군");
		sigungu("43800", "단양군");
		sigungu("44130", "천안시");
		sigungu("44131", "천안시 동남구");
		sigungu("44133", "천안시 서북구");
		sigungu("44150", "공주시");
		sigungu("44180", "보령시");
		sigungu("44200", "아산시");
		sigungu("44210", "서산시");
		sigungu("44230", "논산시");
		sigungu("44250", "계룡시");
		sigungu("44270", "당진시");
		sigungu("44710", "금산군");
		sigungu("44760", "부여군");
		sigungu("44770", "서천군");
		sigungu("44790", "청양군");
		sigungu("44800", "홍성군");
		sigungu("44810", "예산군");
		sigungu("44825", "태안군");
		sigungu("52111", "전주시 완산구");
		sigungu("52113", "전주시 덕진구");
		sigungu("52130", "군산시");
		sigungu("52140", "익산시");
		sigungu("52180", "정읍시");
		sigungu("52190", "남원시");
		sigungu("52210", "김제시");
		sigungu("52710", "완주군");
		sigungu("52720", "진안군");
		sigungu("52730", "무주군");
		sigungu("52740", "장수군");
		sigungu("52750", "임실군");
		sigungu("52770", "순창군");
		sigungu("52790", "고창군");
		sigungu("52800", "부안군");
		sigungu("12110", "목포시");
		sigungu("12130", "여수시");
		sigungu("12150", "순천시");
		sigungu("12170", "나주시");
		sigungu("12190", "광양시");
		sigungu("12210", "동구");
		sigungu("12240", "서구");
		sigungu("12270", "남구");
		sigungu("12300", "북구");
		sigungu("12330", "광산구");
		sigungu("12710", "담양군");
		sigungu("12720", "곡성군");
		sigungu("12730", "구례군");
		sigungu("12740", "고흥군");
		sigungu("12750", "보성군");
		sigungu("12760", "화순군");
		sigungu("12770", "장흥군");
		sigungu("12780", "강진군");
		sigungu("12790", "해남군");
		sigungu("12800", "영암군");
		sigungu("12810", "무안군");
		sigungu("12820", "함평군");
		sigungu("12830", "영광군");
		sigungu("12840", "장성군");
		sigungu("12850", "완도군");
		sigungu("12860", "진도군");
		sigungu("12870", "신안군");
		sigungu("47111", "포항시 남구");
		sigungu("47113", "포항시 북구");
		sigungu("47130", "경주시");
		sigungu("47150", "김천시");
		sigungu("47170", "안동시");
		sigungu("47190", "구미시");
		sigungu("47210", "영주시");
		sigungu("47230", "영천시");
		sigungu("47250", "상주시");
		sigungu("47280", "문경시");
		sigungu("47290", "경산시");
		sigungu("47730", "의성군");
		sigungu("47750", "청송군");
		sigungu("47760", "영양군");
		sigungu("47770", "영덕군");
		sigungu("47820", "청도군");
		sigungu("47830", "고령군");
		sigungu("47840", "성주군");
		sigungu("47850", "칠곡군");
		sigungu("47900", "예천군");
		sigungu("47920", "봉화군");
		sigungu("47930", "울진군");
		sigungu("47940", "울릉군");
		sigungu("48121", "창원시 의창구");
		sigungu("48123", "창원시 성산구");
		sigungu("48125", "창원시 마산합포구");
		sigungu("48127", "창원시 마산회원구");
		sigungu("48129", "창원시 진해구");
		sigungu("48170", "진주시");
		sigungu("48220", "통영시");
		sigungu("48240", "사천시");
		sigungu("48250", "김해시");
		sigungu("48270", "밀양시");
		sigungu("48310", "거제시");
		sigungu("48330", "양산시");
		sigungu("48720", "의령군");
		sigungu("48730", "함안군");
		sigungu("48740", "창녕군");
		sigungu("48820", "고성군");
		sigungu("48840", "남해군");
		sigungu("48850", "하동군");
		sigungu("48860", "산청군");
		sigungu("48870", "함양군");
		sigungu("48880", "거창군");
		sigungu("48890", "합천군");
		sigungu("50110", "제주시");
		sigungu("50130", "서귀포시");
	}

	private static void sigungu(String code, String name) {
		SIGUNGU.put(code, name);
	}

	/**
	 * 없어졌거나(인천 2026-07 재편: 중구·동구 → 제물포구·영종구, 서구 → 서해구·검단구) 아래 구로 나뉜 시(화성시, 천안시)의 코드 →
	 * 지금 고를 수 있는 코드. 공고에는 옛 코드·시 코드가 남아 있어서 비교할 때 넓혀 본다. 사용자 선택지에서는 뺀다.
	 */
	private static final Map<String, List<String>> SUCCESSORS = Map.of(
			"28110", List.of("28125", "28155"),
			"28140", List.of("28125"),
			"28260", List.of("28275", "28290"),
			"41590", List.of("41591", "41593", "41595", "41597"),
			"44130", List.of("44131", "44133"));

	/** 시군구 이름에서 지역을 가리키지 않는 끝 글자. "천안" 처럼 줄여 써도 "천안시" 를 찾는다 */
	private static final List<String> SIGUNGU_SUFFIXES = List.of("시", "군", "구");

	private Regions() {
	}

	/** "11620" → "관악구", "41463" → "용인시 기흥구". 모르는 코드는 null. */
	public static String sigunguName(String code) {
		return SIGUNGU.get(code);
	}

	/** 화면 표시용: "11620" → "서울 관악구", "28" → "인천". */
	public static String displayName(String code) {
		if (code == null || code.length() < 2) {
			return code;
		}
		String sido = SIDO_NAMES.getOrDefault(code.substring(0, 2), code.substring(0, 2));
		if (code.length() == 2) {
			return sido;
		}
		String name = SIGUNGU.get(code);
		return name == null ? sido + " (" + code + ")" : sido.equals("세종") ? sido : sido + " " + name;
	}

	/**
	 * 코드 목록의 화면 이름. 한 시의 구가 모두 들어 있으면 시로 묶는다:
	 * ["41461", "41463", "41465"] → ["경기 용인시"], ["41463"] → ["경기 용인시 기흥구"], ["28"] → ["인천"].
	 */
	public static List<String> displayNamesOf(List<String> codes) {
		List<String> out = new java.util.ArrayList<>();
		for (String code : codes) {
			String name = SIGUNGU.get(code);
			String city = name != null && name.contains(" ") ? name.substring(0, name.indexOf(' ')) : null;
			String sido = code.length() >= 2 ? code.substring(0, 2) : code;
			List<String> siblings = city == null ? List.of()
					: SIGUNGU.entrySet()
						.stream()
						.filter(e -> e.getKey().startsWith(sido) && e.getValue().startsWith(city + " ") && isSelectable(e.getKey()))
						.map(Map.Entry::getKey)
						.toList();
			String display = !siblings.isEmpty() && codes.containsAll(siblings)
					? SIDO_NAMES.getOrDefault(sido, sido) + " " + city : displayName(code);
			if (!out.contains(display)) {
				out.add(display);
			}
		}
		return out;
	}

	/** 사용자가 고를 수 있는 시군구 코드인가 (없어진 코드, 구로 나뉜 시 코드는 아님) */
	public static boolean isSelectable(String code) {
		return SIGUNGU.containsKey(code) && !SUCCESSORS.containsKey(code);
	}

	/** 시도 순서대로 고를 수 있는 시군구: {"11": ["11110", ...], ...} */
	public static Map<String, List<String>> selectableBySido() {
		Map<String, List<String>> out = new LinkedHashMap<>();
		SIDO_NAMES.keySet().forEach(sido -> out.put(sido, new java.util.ArrayList<>()));
		SIGUNGU.keySet().stream().filter(Regions::isSelectable).forEach(c -> out.get(c.substring(0, 2)).add(c));
		return out;
	}

	public static String sidoName(String sidoCode) {
		return SIDO_NAMES.get(sidoCode);
	}

	/** 공고의 시군구 목록에 옛 코드·시 코드가 있으면 지금 코드를 덧붙인다(원래 코드도 남긴다). */
	public static List<String> withSuccessors(List<String> codes) {
		java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>(codes);
		codes.forEach(c -> out.addAll(SUCCESSORS.getOrDefault(c, List.of())));
		return List.copyOf(out);
	}

	/**
	 * 학교 장학 거주지 이름 → 코드 앞부분 목록. 시도면 ["28"], 시군구면 ["51110"], 구가 있는 시면 그 구들(["41461", "41463", "41465"]).
	 * "울산 남구", "광주광역시 광산구" 처럼 시도를 앞에 붙인 이름도 읽는다. 동·읍·면 단위이거나 이름이 여러 곳에 있으면("남구", "고성군")
	 * 정할 수 없어 빈 목록.
	 */
	public static List<String> resolve(String name) {
		if (name == null || name.isBlank()) {
			return List.of();
		}
		String sido = sidoCodeOf(name);
		if (sido != null) {
			return List.of(sido);
		}
		List<String> tokens = new java.util.ArrayList<>(Arrays.asList(name.strip().split("\\s+")));
		String sidoFilter = tokens.size() > 1 ? sidoCodeOf(tokens.getFirst()) : null;
		if (sidoFilter != null) {
			tokens.removeFirst();
		}
		String rest = String.join(" ", tokens);
		List<String> matches = SIGUNGU.entrySet()
			.stream()
			.filter(e -> sidoFilter == null || e.getKey().startsWith(sidoFilter))
			.filter(e -> !SUCCESSORS.containsKey(e.getKey()))
			.filter(e -> sameName(e.getValue(), rest))
			.map(Map.Entry::getKey)
			.toList();
		// 한 시군구(구가 있는 시는 그 구들)로 정해질 때만 쓴다
		long places = matches.stream().map(c -> SIGUNGU.get(c).split(" ")[0] + c.substring(0, 2)).distinct().count();
		return places == 1 ? matches : List.of();
	}

	/** "용인시 기흥구" 는 "용인시 기흥구", "용인시", "용인", "기흥구" 와 같다. 한 글자("중" 등)로는 찾지 않는다. */
	private static boolean sameName(String sigungu, String query) {
		String q = query.replaceAll("\\s", "");
		if (q.equals(sigungu.replaceAll("\\s", ""))) {
			return true;
		}
		return Arrays.stream(sigungu.split(" ")).anyMatch(part -> q.equals(part) || q.length() >= 2 && q.equals(stem(part)));
	}

	private static String stem(String name) {
		return SIGUNGU_SUFFIXES.stream()
			.filter(name::endsWith)
			.findFirst()
			.map(suffix -> name.substring(0, name.length() - suffix.length()))
			.orElse(name);
	}

	/** "인천광역시" → "28". 시도 이름이 아니면(시군구·동 이름 등) null. */
	public static String sidoCodeOf(String name) {
		return name == null ? null : SIDO_ALIASES.get(name.replaceAll("\\s", ""));
	}

	/** zipCd 콤마 목록 → 시도 코드 목록 (위 표 순서, 중복 없음). */
	public static List<String> sidoCodes(String zipCd) {
		if (zipCd == null || zipCd.isBlank()) {
			return List.of();
		}
		List<String> prefixes = Arrays.stream(zipCd.split(","))
			.map(String::strip)
			.filter(code -> code.length() >= 2)
			.map(code -> code.substring(0, 2))
			.distinct()
			.toList();
		List<String> known = SIDO_NAMES.keySet().stream().filter(prefixes::contains).toList();
		List<String> unknown = prefixes.stream().filter(p -> !SIDO_NAMES.containsKey(p)).sorted().toList();
		return java.util.stream.Stream.concat(known.stream(), unknown.stream()).toList();
	}

	public static boolean isNationwide(List<String> sidoCodes) {
		return sidoCodes.containsAll(SIDO_NAMES.keySet());
	}

	/** 화면 표시용 이름. 전국이면 ["전국"]. */
	public static List<String> displayNames(boolean nationwide, List<String> sidoCodes) {
		if (nationwide) {
			return List.of("전국");
		}
		return sidoCodes.stream().map(code -> SIDO_NAMES.getOrDefault(code, code)).toList();
	}

}
