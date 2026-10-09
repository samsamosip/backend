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

}
