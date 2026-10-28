package com.youthbenefit.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentGuidesTest {

	@Test
	void findsWhereToGetDocuments() {
		assertThat(DocumentGuides.of("주민등록등본 1부").label()).isEqualTo("정부24 · 무료 · 온라인 발급");
		assertThat(DocumentGuides.of("주민등록 초본(주소변동 포함)").issuer()).isEqualTo("정부24");
		assertThat(DocumentGuides.of("가족관계증명서(상세)").issuer()).isEqualTo("대법원 전자가족관계등록시스템");
		assertThat(DocumentGuides.of("건강보험자격득실확인서").issuer()).isEqualTo("국민건강보험공단");
		assertThat(DocumentGuides.of("소득금액증명원").issuer()).isEqualTo("홈택스");
		assertThat(DocumentGuides.of("지방세 납세증명서").issuer()).isEqualTo("위택스");
		assertThat(DocumentGuides.of("국세 납세증명서").issuer()).isEqualTo("홈택스");
		assertThat(DocumentGuides.of("부동산 등기부등본").issuer()).isEqualTo("인터넷등기소");
		assertThat(DocumentGuides.of("통장 사본").issuer()).isEqualTo("은행 앱·지점");
		assertThat(DocumentGuides.of("재학증명서").issuer()).isEqualTo("다니는(다닌) 학교");
		assertThat(DocumentGuides.of("사업참여 신청서").issuer()).isEqualTo("공고 첨부 양식");
		assertThat(DocumentGuides.of("사업참여 신청서").url()).isNull();
		assertThat(DocumentGuides.of("개인정보 수집·이용 동의서").issuer()).isEqualTo("공고 첨부 양식");
		assertThat(DocumentGuides.of("임대차계약서 사본").issuer()).isEqualTo("본인 보관 서류");
		assertThat(DocumentGuides.of("자격증 사본").issuer()).isEqualTo("본인 보관 서류");
		assertThat(DocumentGuides.of("사업자등록여부 사실증명(필수)").issuer()).isEqualTo("홈택스");
		assertThat(DocumentGuides.of("건강보험득실확인서").issuer()).isEqualTo("국민건강보험공단");
		assertThat(DocumentGuides.of("확정일자부 임대차(전세)계약서 사본").issuer()).isEqualTo("본인 보관 서류");
		assertThat(DocumentGuides.of("주민등록등(초)본").issuer()).isEqualTo("정부24");
		assertThat(DocumentGuides.of("주민등록증 사본").issuer()).isEqualTo("본인 보관 서류");
		assertThat(DocumentGuides.of("학자금 지원구간 통지서").issuer()).isEqualTo("한국장학재단");
		assertThat(DocumentGuides.of("이력서").issuer()).isEqualTo("공고 첨부 양식");
		assertThat(DocumentGuides.of("출입국사실증명서").issuer()).isEqualTo("정부24");
		assertThat(DocumentGuides.of("임대료 납부확인서")).isNull();
		assertThat(DocumentGuides.of("포트폴리오")).isNull();
	}

}
