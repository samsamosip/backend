package com.youthbenefit.eligibility;

import com.youthbenefit.eligibility.Condition.All;
import com.youthbenefit.eligibility.Condition.Check;
import com.youthbenefit.eligibility.Condition.Cmp;
import com.youthbenefit.eligibility.Condition.Not;
import com.youthbenefit.eligibility.Condition.Unparsed;
import com.youthbenefit.policy.Policy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 온통청년 공고의 자격 칸을 조건 나무로 바꾼다 (조건 JSON 명세 3번 "자격 조건 필드" 표).
 * "제한없음" 코드나 0 은 조건이 없다는 뜻이라 노드를 만들지 않는다. 글로만 적힌 조건은 AI 가 붙기 전까지 Unparsed(미확인)다.
 */
public final class OntongConditions {

	private static final String JOB_UNRESTRICTED = "0013010";
	private static final String SCHOOL_UNRESTRICTED = "0049010";
	private static final String MARRIAGE_UNRESTRICTED = "0055003";
	private static final String MAJOR_UNRESTRICTED = "0011009";
	private static final String SPECIAL_UNRESTRICTED = "0014010";
	private static final String INCOME_ANNUAL = "0043002";
	private static final String INCOME_OTHER = "0043003";

	private OntongConditions() {
	}

	public static Condition from(Policy p) {
		List<Condition> children = new ArrayList<>();
		age(p, children);
		if (!p.isNationwide() && p.getZipCd() != null) {
			children.add(new Check("zipCd", Cmp.IN, split(p.getZipCd()), "zipCd"));
		}
		income(p, children);
		codes(children, "jobCd", p.getJobCd(), JOB_UNRESTRICTED, Cmp.IN);
		codes(children, "schoolCd", p.getSchoolCd(), SCHOOL_UNRESTRICTED, Cmp.IN);
		codes(children, "mrgSttsCd", p.getMrgSttsCd(), MARRIAGE_UNRESTRICTED, Cmp.IN);
		codes(children, "plcyMajorCd", p.getPlcyMajorCd(), MAJOR_UNRESTRICTED, Cmp.IN);
		codes(children, "sbizCd", p.getSbizCd(), SPECIAL_UNRESTRICTED, Cmp.INTERSECTS);
		if (p.getAddAplyQlfcCndCn() != null) {
			children.add(new Unparsed("추가 자격 조건", p.getAddAplyQlfcCndCn()));
		}
		if (p.getPtcpPrpTrgtCn() != null) {
			children.add(new Not(new Unparsed("참여 제한 대상", p.getPtcpPrpTrgtCn())));
		}
		return new All(children);
	}

	/** 최대 나이가 이 값 이상이면 상한이 없다는 뜻으로 본다 (실측: 99·100·120·999 가 "제한 없음" 대신 쓰임). */
	static final int NO_UPPER_AGE = 99;

	/**
	 * 나이 제한 여부 칸(sprtTrgtAgeLmtYn)은 실측상 믿을 수 없어(Y 인데 나이가 있는 공고 673건) 최소·최대 나이 숫자만 본다.
	 * "19~120세"는 "19세 이상"으로 바꾼다.
	 */
	private static void age(Policy p, List<Condition> out) {
		int min = p.getSprtTrgtMinAge() == null ? 0 : p.getSprtTrgtMinAge();
		int max = p.getSprtTrgtMaxAge() == null || p.getSprtTrgtMaxAge() >= NO_UPPER_AGE ? 0 : p.getSprtTrgtMaxAge();
		String evidence = "sprtTrgtMinAge=" + p.getSprtTrgtMinAge() + ", sprtTrgtMaxAge=" + p.getSprtTrgtMaxAge();
		if (min > 0 && max > 0) {
			out.add(new Check("age", Cmp.BETWEEN, List.of(String.valueOf(min), String.valueOf(max)), evidence));
		}
		else if (min > 0) {
			out.add(new Check("age", Cmp.GTE, List.of(String.valueOf(min)), evidence));
		}
		else if (max > 0) {
			out.add(new Check("age", Cmp.LTE, List.of(String.valueOf(max)), evidence));
		}
	}

	private static void income(Policy p, List<Condition> out) {
		if (INCOME_ANNUAL.equals(p.getEarnCndSeCd())) {
			if (p.getEarnMaxAmt() != null && p.getEarnMaxAmt() > 0) {
				out.add(new Check("annualIncome", Cmp.LTE, List.of(String.valueOf(p.getEarnMaxAmt())),
						"earnMaxAmt=" + p.getEarnMaxAmt()));
			}
			if (p.getEarnMinAmt() != null && p.getEarnMinAmt() > 0) {
				out.add(new Check("annualIncome", Cmp.GTE, List.of(String.valueOf(p.getEarnMinAmt())),
						"earnMinAmt=" + p.getEarnMinAmt()));
			}
		}
		else if (INCOME_OTHER.equals(p.getEarnCndSeCd())) {
			out.add(new Unparsed("소득 조건", p.getEarnEtcCn() != null ? p.getEarnEtcCn() : "소득 조건이 글로만 안내됨"));
		}
	}

	private static void codes(List<Condition> out, String var, String commaCodes, String unrestricted, Cmp cmp) {
		List<String> codes = split(commaCodes);
		if (!codes.isEmpty() && !codes.contains(unrestricted)) {
			out.add(new Check(var, cmp, codes, var + "=" + commaCodes));
		}
	}

	private static List<String> split(String comma) {
		if (comma == null || comma.isBlank()) {
			return List.of();
		}
		return Arrays.stream(comma.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
	}

}
