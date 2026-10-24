package com.youthbenefit.notification;

/** 알림 종류. 화면 탭은 type 과 (변경 알림이면) ChangeKind 로 나눈다. */
public enum NotificationType {

	CHANGE("공고 변경"),
	NEW_MATCHES("새 공고"),
	DEADLINE("마감 임박");

	private final String label;

	NotificationType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
