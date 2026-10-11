package com.youthbenefit.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.youthbenefit.application.DocumentListParser.Document;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 실제 온통청년 제출 서류 글로 만든 사례. */
class DocumentListParserTest {

	private static List<String> names(String raw) {
		return DocumentListParser.parse(raw).stream().map(Document::name).toList();
	}

	@Test
	void commaListKeepsCommasInsideParentheses() {
		assertThat(names("사업신청서\n거주지 증빙자료(주민등록초본, 재학증명서, 재직증명서 등)\n※ 주민등록초본 : 공고일 이후 발급"))
			.containsExactly("사업신청서", "거주지 증빙자료(주민등록초본, 재학증명서, 재직증명서 등)");
		assertThat(names("주민등록등본, 주민등록초본, 고용보험 자격이력내역서, 개인정보제공동의서 등 4종"))
			.containsExactly("주민등록등본", "주민등록초본", "고용보험 자격이력내역서", "개인정보제공동의서");
	}

	@Test
	void numberedLinesDropHeadersNotesAndColonExplanations() {
		String raw = """
				○ 공통
				① 열차운임비 지원 신청서
				② 개인정보 수집·활용·제공 동의서
				⑤ 주민등록초본(최근 5년): 행정정보공동이용 동의 시 제출하지 않음
				○ 학생
				⑧ 정기권 사용 기간 동안 재학을 증명하는 서류(재학증명서 등)
				※ 첨부파일이 많은 경우 pdf로 변환하여 업로드 부탁드립니다.""";

		assertThat(names(raw)).containsExactly("열차운임비 지원 신청서", "개인정보 수집·활용·제공 동의서", "주민등록초본(최근 5년)",
				"정기권 사용 기간 동안 재학을 증명하는 서류(재학증명서 등)");
	}

	@Test
	void inlineCircledNumbersAreSplit() {
		assertThat(names("(공    통) ① 신청서 ② 주민등록초본 ③ 농업경영체 등록 확인서④ 건강보험자격확인서"))
			.containsExactly("신청서", "주민등록초본", "농업경영체 등록 확인서", "건강보험자격확인서");
	}

	@Test
	void optionalTagsAreMarked() {
		List<Document> docs = DocumentListParser
			.parse("[필수] 지원서 1부, 주민등록초본 1부 / [선택] 학력 증명서 또는 수료증, 관련분야 자격증 사본 1부");

		assertThat(docs).containsExactly(new Document("지원서 1부", false), new Document("주민등록초본 1부", false),
				new Document("학력 증명서 또는 수료증", true), new Document("관련분야 자격증 사본 1부", true));
		assertThat(DocumentListParser.parse("□ 필수 : 주민등록초본(주소변동이력 포함 / 마이데이터 제출 시 불필요)\n□ 해당자에 한함 : 재학증명서"))
			.containsExactly(new Document("주민등록초본(주소변동이력 포함 / 마이데이터 제출 시 불필요)", false),
					new Document("재학증명서", true));
	}

	@Test
	void headerBeforeColonIsNotADocument() {
		assertThat(names("○  필수 제출서류: 신분증(의왕시 주소 필수), 면접증빙서류(면접일시, 성명 명시 필수)"))
			.containsExactly("신분증(의왕시 주소 필수)", "면접증빙서류(면접일시, 성명 명시 필수)");
	}

	@Test
	void pointersToAttachmentsGiveNothing() {
		assertThat(names("☞ 자세한 내용은 붙임파일을 확인해주시기 바랍니다.")).isEmpty();
		assertThat(names("별도 문의")).isEmpty();
		assertThat(names("홍성군 공고 제2026-78호 참고")).isEmpty();
		assertThat(names("-")).isEmpty();
		assertThat(names(null)).isEmpty();
	}

	@Test
	void letterMarkersAndFootnotesAreRemoved() {
		String raw = """
				○ 지원 시: 해당 서류(가~마) 모두 제출한 경우에만 접수 가능
				가. 우울증 치료비 지원 신청서 1부(서식 1)
				라. 주민등록초본 1부 *공고일 이후 발급분(주소변동사항 포함)만 인정
				○ 선정 후(치료비 신청 시)
				라. 본인 명의 통장 사본
				가) 일반근로자
				다) 소득이 없는 경우""";

		assertThat(names(raw)).containsExactly("우울증 치료비 지원 신청서 1부(서식 1)", "주민등록초본 1부", "본인 명의 통장 사본");
		assertThat(names("○ 증명사진(3*4)\n○ 신분증 사본\n○ 혼인관계증명서(상세*)"))
			.containsExactly("증명사진(3*4)", "신분증 사본", "혼인관계증명서(상세*)");
	}

	@Test
	void sectionHeadersAndSentencesAreNotDocuments() {
		String raw = """
				&lt;민원인이 제출해야 하는 서류&gt;
				○ 신청인 제출 서류
				- 보증료 지원 신청서, 서약서
				(근로소득) 아래 서류 중 택1
				· 최근년도 근로소득원천징수 영수증
				- 대리신청의 경우 위임장, 본인-대리인 관계 증명 서류 제출
				→ 서류는 제출 항목별로 압축 파일로 저장하여 업로드
				(서울 생활권자) 재학·휴학증명서, 사업자등록증 중 택 1""";

		assertThat(names(raw)).containsExactly("보증료 지원 신청서", "서약서", "최근년도 근로소득원천징수 영수증", "대리신청의 경우 위임장",
				"본인-대리인 관계 증명 서류", "재학·휴학증명서", "사업자등록증");
		assertThat(names("해당없음")).isEmpty();
		assertThat(names("담당자 확인필요")).isEmpty();
	}

	@Test
	void colonsAndSlashesExplainTheDocument() {
		// 괄호 안 콜론은 그대로, 번호 줄의 " / " 뒤 발급처는 버리고, "발생일/신고일" 은 설명이라 나누지 않는다
		assertThat(names("- 2026 건강보험 납부확인서(취창업자:본인, 기혼자:본인 및 배우자)"))
			.containsExactly("2026 건강보험 납부확인서(취창업자:본인, 기혼자:본인 및 배우자)");
		assertThat(names("① 사업신청서 / (붙임3)\n⑦ 국세 완납증명서  1부 / 홈텍스, 세무서"))
			.containsExactly("사업신청서", "국세 완납증명서  1부");
		assertThat(names("1. 주민등록표 초본: 신청월 발급본, 발생일/신고일 포함, 마지막장까지 제출\n5. 통장 사본: 은행명, 성명, 계좌번호 표기"))
			.containsExactly("주민등록표 초본", "통장 사본");
		assertThat(names("과제 1 : 드라마 기획안 작성\n- 분량 : A4 3페이지 이내")).isEmpty();
	}

	@Test
	void wrappedInlineListIsJoinedAndNotesAreSkipped() {
		assertThat(names("⑦법인등기부등본(법인일 시) ⑧주민등록\n초본(최근 5년, 주민번호 표시) ⑨사실증명(총사업이력내역)"))
			.containsExactly("법인등기부등본(법인일 시)", "주민등록초본(최근 5년, 주민번호 표시)", "사실증명(총사업이력내역)");
		assertThat(names("② 개인정보 수집·이용 동의서\n※ 위 파일을 묶어서 제출(①,②한글파일, ③PDF파일로 제출)"))
			.containsExactly("개인정보 수집·이용 동의서");
	}

}
