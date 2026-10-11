package com.youthbenefit.harvester;

import static org.assertj.core.api.Assertions.assertThat;

import com.youthbenefit.application.DocumentListParser;
import com.youthbenefit.application.DocumentListParser.Document;
import com.youthbenefit.eligibility.ConditionResult;
import com.youthbenefit.eligibility.EligibilityService;
import com.youthbenefit.eligibility.PolicyEligibility;
import com.youthbenefit.eligibility.ProfileFacts;
import com.youthbenefit.eligibility.Truth;
import com.youthbenefit.eligibility.Verdict;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyContent;
import com.youthbenefit.policy.PolicySource;
import com.youthbenefit.profile.EnrollmentStatus;
import com.youthbenefit.profile.ProfileRequest;
import com.youthbenefit.profile.UserProfile;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 학교 장학(policy-harvester) 공고를 저장 형태로 바꾸고 판정하는 규칙. */
class HarvesterRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

	private final HarvesterPolicyMapper mapper = new HarvesterPolicyMapper(JsonMapper.builder().build());

	private final EligibilityService service = new EligibilityService(null, null);

	private String detail;

	@BeforeEach
	void setUp() throws IOException {
		try (InputStream in = getClass().getResourceAsStream("/fixtures/harvester-detail.json")) {
			detail = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private Policy policy(String json) {
		PolicyContent content = mapper.toContent(Map.of("version_id", "v2"), json);
		return Policy.create(PolicySource.SCHOOL, "11111111-1111-4111-8111-111111111111", content);
	}

	/** 인천 연수구, 대학 재학, 인하대 3학년, 평점 3.8/4.5, 학자금 5구간 */
	private static ProfileRequest student(String zip, String school, Integer grade, String gpa, String scale) {
		return new ProfileRequest(LocalDate.of(2004, 3, 1), zip, null, "0013003", "0049005", null, null, null, null,
				null, null, null, null, null, school, EnrollmentStatus.ENROLLED, grade,
				gpa == null ? null : new BigDecimal(gpa), scale == null ? null : new BigDecimal(scale), 5);
	}

	private PolicyEligibility judge(Policy policy, ProfileRequest request) {
		UserProfile profile = UserProfile.create(UUID.randomUUID());
		profile.replace(request);
		return service.judge(policy, ProfileFacts.of(profile, TODAY));
	}

	private static Truth truthOf(PolicyEligibility e, String label) {
		return e.conditions().stream().filter(c -> c.label().equals(label)).map(ConditionResult::truth).findFirst()
			.orElseThrow();
	}

	@Test
	void mapsScholarshipToPolicy() {
		Policy p = policy(detail);

		assertThat(p.getApplyStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(p.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(p.getApplyPeriodCode()).isEqualTo("0057001");
		assertThat(p.getCategoryGroup()).isEqualTo("교육");
		assertThat(p.getSidoCodes()).isEqualTo("28");
		assertThat(p.getApplyUrl()).isEqualTo("https://example.org/apply");
		assertThat(p.getSourceVersion()).isEqualTo("v2");
		// 정리된 서류 목록을 그대로 쓴다 — 글 쪼개기였다면 "첨부"가 들어간 신청서가 빠졌을 것
		assertThat(DocumentListParser.documents(p)).containsExactly(new Document("장학금 신청서 (첨부파일 참조)", false),
				new Document("기초생활수급자 증명서", true));
	}

	@Test
	void closedOrUnknownWithoutDatesIsClosed() {
		assertThat(HarvesterPolicyMapper.applyPeriodCode("closed", LocalDate.of(2027, 1, 1))).isEqualTo("0057003");
		assertThat(HarvesterPolicyMapper.applyPeriodCode("unknown", null)).isEqualTo("0057003");
		assertThat(HarvesterPolicyMapper.applyPeriodCode("active", null)).isEqualTo("0057002");
		assertThat(HarvesterSyncService.isOldAcademicYear(2024, TODAY)).isTrue();
		assertThat(HarvesterSyncService.isOldAcademicYear(2025, TODAY)).isFalse();
		assertThat(HarvesterSyncService.isOldAcademicYear(null, TODAY)).isFalse();
	}

	@Test
	void studentMeetingEveryRuleStillNeedsCheckForTextCondition() {
		PolicyEligibility e = judge(policy(detail), student("28185", "인하대", 3, "3.8", "4.5"));

		// 정리된 조건은 모두 맞고, 글로 남은 조건("직전 학기 12학점 이상") 때문에 확인 필요
		assertThat(e.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
		assertThat(e.conditions()).filteredOn(c -> c.truth() == Truth.FALSE).isEmpty();
		assertThat(truthOf(e, "학교")).isEqualTo(Truth.TRUE);
		assertThat(truthOf(e, "성적")).isEqualTo(Truth.TRUE);
		assertThat(truthOf(e, "학자금 지원구간")).isEqualTo(Truth.TRUE);
		assertThat(e.conditions()).extracting(ConditionResult::requirement)
			.contains("평점 3.0 이상 (4.5 만점)", "2~4학년", "인천 거주 (주민등록)", "학자금 지원구간 8구간 이하");

		PolicyEligibility noText = judge(policy(detail.replace("직전 학기 12학점 이상 이수", "")),
				student("28185", "인하대학교", 3, "3.8", "4.5"));
		assertThat(noText.verdict()).isEqualTo(Verdict.MATCH);
	}

	@Test
	void definiteMismatchesAndUnknowns() {
		Policy p = policy(detail);

		assertThat(judge(p, student("28185", "인하대", 1, "3.8", "4.5")).verdict()).isEqualTo(Verdict.NO_MATCH);
		assertThat(judge(p, student("28185", "연세대학교", 3, "3.8", "4.5")).verdict()).isEqualTo(Verdict.NO_MATCH);
		assertThat(judge(p, student("28185", "인하대", 3, "2.5", "4.5")).verdict()).isEqualTo(Verdict.NO_MATCH);

		// 만점이 다르면 환산하지 않고 확인 필요
		assertThat(truthOf(judge(p, student("28185", "인하대", 3, "3.8", "4.3")), "성적")).isEqualTo(Truth.UNKNOWN);
		// 서울에 살아도 보호자가 인천에 살면 되므로 불일치가 아니라 확인 필요
		PolicyEligibility seoul = judge(p, student("11620", "인하대", 3, "3.8", "4.5"));
		assertThat(seoul.verdict()).isEqualTo(Verdict.NEEDS_CHECK);
		// 학교 항목을 비워 두면 그 항목이 missingFields 로 나온다
		PolicyEligibility empty = judge(p, student("28185", null, null, null, null));
		assertThat(empty.missingFields()).contains("schoolName", "gradeLevel", "gpa");
	}

	@Test
	void sigunguResidenceIsComparedButDongAndGenericSchoolsStayUnknown() {
		String chuncheon = detail.replace("\"인천광역시\"", "\"춘천시\"").replace("\"self_or_parent\"", "\"self\"");
		assertThat(judge(policy(chuncheon), student("51110", "인하대", 3, "3.8", "4.5")).conditions())
			.filteredOn(c -> c.label().equals("지역"))
			.singleElement()
			.satisfies(c -> {
				assertThat(c.truth()).isEqualTo(Truth.TRUE);
				assertThat(c.requirement()).isEqualTo("강원 춘천시 거주 (주민등록)");
				assertThat(c.myValue()).isEqualTo("강원 춘천시");
			});
		assertThat(judge(policy(chuncheon), student("28185", "인하대", 3, "3.8", "4.5")).verdict())
			.isEqualTo(Verdict.NO_MATCH);

		String json = detail.replace("\"인천광역시\"", "\"석남1동\"").replace("[\"인하대학교\"]", "[\"국내 4년제 대학\"]");
		PolicyEligibility e = judge(policy(json), student("28185", "인하대", 3, "3.8", "4.5"));
		assertThat(truthOf(e, "거주지")).isEqualTo(Truth.UNKNOWN);
		assertThat(truthOf(e, "학교")).isEqualTo(Truth.UNKNOWN);
	}

}
