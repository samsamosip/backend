package com.youthbenefit.notification;

/** 공고가 어떻게 바뀌었는지. 학교 장학은 Harvester 가 알려 준 종류를 쓰고, 나머지는 바뀐 값으로 짐작한다. */
public enum ChangeKind {

	EXTENDED("마감 연장"),
	CORRECTED("정정"),
	CANCELLED("취소"),
	CLOSED("마감"),
	REMOVED("공고 내림"),
	RESTORED("다시 게시"),
	UPDATED("내용 변경");

	private final String label;

	ChangeKind(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
