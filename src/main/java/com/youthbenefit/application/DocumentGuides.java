package com.youthbenefit.application;

import java.util.List;
import java.util.Locale;

/**
 * 서류 이름 → 어디서 발급받는지 (준비함 체크리스트 서류 아래 회색 한 줄: "정부24 · 무료 · 온라인 발급 →"). 자주 나오는 서류만 정리했고
 * 모르면 null(그 줄 없음). 먼저 맞는 규칙을 쓰므로 구체적인 것을 앞에 둔다("건강보험자격득실" 이 "건강보험" 보다 먼저).
 *
 * <p>
 * 링크는 각 기관 첫 화면이다 — 세부 메뉴 주소는 자주 바뀌어서 깨진 링크를 보여 주지 않으려고. 수수료는 온라인 발급 기준.
 */
public final class DocumentGuides {

	/**
	 * @param issuer 발급처 ("정부24")
	 * @param fee "무료" / "수수료 있음" / null(모름)
	 * @param online 온라인으로 바로 발급되는지
	 * @param url 누르면 갈 곳, 없으면 null(공고 첨부 양식 → 원문 보기)
	 * @param label 화면에 그대로 쓰는 한 줄 ("정부24 · 무료 · 온라인 발급")
	 */
	public record Guide(String issuer, String fee, boolean online, String url, String label) {

		static Guide of(String issuer, String fee, boolean online, String url) {
			StringBuilder label = new StringBuilder(issuer);
			if (fee != null) {
				label.append(" · ").append(fee);
			}
			label.append(online ? " · 온라인 발급" : "");
			return new Guide(issuer, fee, online, url, label.toString());
		}

	}

	private record Rule(List<String> words, Guide guide) {
	}

	private static final String GOV24 = "https://www.gov.kr";

	private static final String EFAMILY = "https://efamily.scourt.go.kr";

	private static final String HOMETAX = "https://www.hometax.go.kr";

	private static final String NHIS = "https://www.nhis.or.kr";

	private static final String WORK24 = "https://www.work24.go.kr";

	private static final List<Rule> RULES = List.of(
			// 공고마다 따로 있는 양식
			rule(Guide.of("공고 첨부 양식", null, false, null), "신청서", "동의서", "서약서", "계획서", "확약서", "자기소개서", "추천서",
					"신고서", "이력서", "지원서", "위임장"),
			// 가족관계등록부 (대법원)
			rule(Guide.of("대법원 전자가족관계등록시스템", "무료", true, EFAMILY), "가족관계증명", "혼인관계증명", "기본증명", "입양관계증명",
					"친양자입양관계증명"),
			// 건강보험
			rule(Guide.of("국민건강보험공단", "무료", true, NHIS), "건강보험자격득실", "자격득실확인", "건강보험득실", "건강보험료납부", "건강보험료 납부",
					"건강보험증", "보험료납부확인"),
			// 지방세 (홈택스의 "납세증명" 보다 먼저)
			rule(Guide.of("위택스", "무료", true, "https://www.wetax.go.kr"), "지방세납세증명", "지방세 납세증명", "지방세세목별",
					"지방세 세목별", "지방세과세증명"),
			// 국세 (홈택스)
			rule(Guide.of("홈택스", "무료", true, HOMETAX), "소득금액증명", "사업자등록증", "사업자등록증명", "근로소득원천징수",
					"원천징수영수증", "휴업사실증명", "폐업사실증명", "부가가치세과세표준", "국세납세증명", "납세증명",
					"사업자등록여부"),
			// 고용보험
			rule(Guide.of("고용24", "무료", true, WORK24), "고용보험피보험자격", "피보험자격이력", "고용보험 피보험", "고용보험가입",
					"구직등록확인", "실업급여"),
			// 4대보험 가입 (사업장)
			rule(Guide.of("4대사회보험 정보연계센터", "무료", true, "https://www.4insure.or.kr"), "4대보험", "4대사회보험",
					"가입자명부"),
			// 등기 (유료)
			rule(Guide.of("인터넷등기소", "수수료 있음", true, "http://www.iros.go.kr"), "등기부", "등기사항전부증명", "등기사항증명"),
			// 자격증
			rule(Guide.of("Q-net(큐넷)", "수수료 있음", true, "https://www.q-net.or.kr"), "국가기술자격"),
			// 학자금
			rule(Guide.of("한국장학재단", "무료", true, "https://www.kosaf.go.kr"), "학자금지원구간", "학자금 지원구간", "소득구간통지"),
			// 병역
			rule(Guide.of("병무청", "무료", true, "https://www.mma.go.kr"), "병적증명", "병적기록"),
			// 학교 서류
			rule(Guide.of("다니는(다닌) 학교", null, true, null), "재학증명", "졸업증명", "졸업예정증명", "성적증명", "휴학증명",
					"수료증명", "학적부", "재적증명"),
			// 은행
			rule(Guide.of("은행 앱·지점", "무료", true, null), "통장사본", "통장 사본", "통장(사본)", "계좌사본", "통장표지"),
			// 직접 가진 것
			rule(Guide.of("본인 보관 서류", null, false, null), "임대차계약서", "전세계약서", "월세계약서", "매매계약서", "신분증", "자격증", "임대차", "주민등록증"),
			// 회사
			rule(Guide.of("다니는(다닌) 회사", null, false, null), "재직증명", "경력증명", "근로계약서", "급여명세", "퇴직증명"),
			// 정부24 (주민등록 등) — 넓은 말이라 맨 뒤
			rule(Guide.of("정부24", "무료", true, GOV24), "주민등록등본", "주민등록초본", "주민등록표등본", "주민등록표초본", "등본",
					"초본", "주민등록", "건축물대장", "토지대장", "출입국사실증명", "장애인증명", "장애인등록증", "수급자증명", "수급자 증명",
					"차상위", "한부모가족증명", "한부모 가족증명", "전입세대", "지방세 완납"));

	private DocumentGuides() {
	}

	private static Rule rule(Guide guide, String... words) {
		return new Rule(List.of(words), guide);
	}

	/** 서류 이름에 맞는 발급처, 모르면 null */
	public static Guide of(String documentName) {
		if (documentName == null) {
			return null;
		}
		String name = documentName.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
		for (Rule rule : RULES) {
			if (rule.words().stream().anyMatch(w -> name.contains(w.replaceAll("\\s+", "").toLowerCase(Locale.ROOT)))) {
				return rule.guide();
			}
		}
		return null;
	}

}
