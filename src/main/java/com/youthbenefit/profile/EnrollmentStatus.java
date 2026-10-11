package com.youthbenefit.profile;

/** 학적 상태. 학교 장학 자격(policy-harvester 의 enrollment_states)과 맞춘다. */
public enum EnrollmentStatus {

	ENROLLED("재학", "enrolled"),
	ON_LEAVE("휴학", "on_leave"),
	INCOMING("입학 예정", "incoming"),
	RETURNING("복학 예정", "returning"),
	COMPLETED("수료·졸업", "completed");

	private final String label;

	private final String harvesterValue;

	EnrollmentStatus(String label, String harvesterValue) {
		this.label = label;
		this.harvesterValue = harvesterValue;
	}

	public String label() {
		return label;
	}

	public static EnrollmentStatus fromHarvester(String value) {
		for (EnrollmentStatus s : values()) {
			if (s.harvesterValue.equals(value)) {
				return s;
			}
		}
		return null;
	}

}
