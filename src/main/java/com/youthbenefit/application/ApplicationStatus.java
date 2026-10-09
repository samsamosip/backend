package com.youthbenefit.application;

/** 준비함 상태. 외부 링크를 눌렀다고 신청 완료로 바꾸지 않는다 — 사용자가 직접 표시한다(기획안 7쪽). */
public enum ApplicationStatus {

	INTERESTED("관심"), PREPARING("준비 중"), APPLIED("신청 완료");

	private final String label;

	ApplicationStatus(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
