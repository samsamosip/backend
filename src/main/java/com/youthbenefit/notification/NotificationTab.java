package com.youthbenefit.notification;

import java.util.List;

/**
 * 알림함 탭 (최종 디자인 05: 전체 · 새 공고 · 마감 연장 · 정정 · 취소). 정정에는 짐작한 "내용 변경"도, 취소에는 "공고 내림"도 넣는다 — 사용자에겐 같은
 * 뜻이라서.
 */
public enum NotificationTab {

	NEW(List.of()), EXTENDED(List.of(ChangeKind.EXTENDED)), CORRECTED(List.of(ChangeKind.CORRECTED, ChangeKind.UPDATED)),
	CANCELLED(List.of(ChangeKind.CANCELLED, ChangeKind.REMOVED));

	private final List<ChangeKind> kinds;

	NotificationTab(List<ChangeKind> kinds) {
		this.kinds = kinds;
	}

	List<ChangeKind> kinds() {
		return kinds;
	}

}
