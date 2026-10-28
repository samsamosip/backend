package com.youthbenefit.profile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 전공 이름("전기전자공학", "경영학과") → 온통청년 전공 계열 코드. 이름에 들어 있는 말로 정하고, 모르면 null(판정에서는 "모름").
 * 순서가 중요하다: "경영정보" 처럼 겹치면 먼저 나온 계열.
 */
public final class MajorCategories {

	private static final Map<String, List<String>> WORDS = new LinkedHashMap<>();

	static {
		// 예체능·농산업을 먼저 (산업디자인·식품공학 등은 그쪽이 더 정확)
		WORDS.put("0011006", List.of("디자인", "미술", "음악", "작곡", "성악", "실용음악", "체육", "스포츠", "무용", "연극", "영화", "공연",
				"사진", "만화", "애니메이션", "웹툰", "조형", "회화", "조소", "공예", "패션", "뷰티", "미용", "방송연예", "연기"));
		WORDS.put("0011007", List.of("농업", "농학", "원예", "축산", "동물자원", "산림", "임학", "수산", "해양생산", "조경", "식물자원", "농생명",
				"농업경제", "스마트팜"));
		WORDS.put("0011005", List.of("공학", "전기", "전자", "기계", "컴퓨터", "소프트웨어", "정보통신", "반도체", "건축", "토목", "화공", "화학공",
				"신소재", "재료", "산업공", "항공", "우주", "자동차", "조선", "로봇", "인공지능", "AI", "데이터사이언스", "보안", "에너지", "환경공",
				"원자력", "전산", "IT", "게임"));
		WORDS.put("0011003", List.of("경영", "경제", "회계", "세무", "무역", "금융", "재무", "마케팅", "유통", "물류", "부동산", "보험", "관광경영",
				"호텔경영", "국제통상", "통상"));
		WORDS.put("0011004", List.of("수학", "물리", "화학", "생명과학", "생물", "통계", "지구과학", "천문", "대기", "해양학", "식품영양", "생화학",
				"의학", "간호", "약학", "치의학", "한의학", "수의학", "보건", "물리치료", "임상병리", "방사선"));
		WORDS.put("0011002", List.of("행정", "정치", "사회", "법학", "법", "심리", "언론", "미디어", "신문방송", "광고홍보", "사회복지", "교육", "유아",
				"아동", "가족", "외교", "군사", "경찰", "소방", "지리", "문헌정보", "도시", "국제"));
		WORDS.put("0011001", List.of("국어", "국문", "영어", "영문", "중어", "중문", "일어", "일문", "불어", "독어", "노어", "서어", "언어", "문학",
				"사학", "역사", "철학", "종교", "신학", "고고", "문화", "인문", "문예창작", "통번역"));
	}

	private MajorCategories() {
	}

	/** 전공 계열 코드, 모르면 null */
	public static String codeOf(String majorName) {
		if (majorName == null || majorName.isBlank()) {
			return null;
		}
		String name = majorName.replaceAll("\\s+", "");
		for (Map.Entry<String, List<String>> e : WORDS.entrySet()) {
			if (e.getValue().stream().anyMatch(name::contains)) {
				return e.getKey();
			}
		}
		return null;
	}

}
