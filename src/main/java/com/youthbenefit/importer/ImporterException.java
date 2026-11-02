package com.youthbenefit.importer;

/** 불러오기 서버 호출 실패 (설정 없음, 키 오류, 연결 실패 등). */
public class ImporterException extends RuntimeException {

	/** 불러오기 서버가 보낸 값을 거절함 (400·413 — 형식 오류, 지원 안 하는 사진, 너무 큰 사진). 다시 보내도 같다 */
	private final boolean rejected;

	public ImporterException(String message) {
		this(message, null, false);
	}

	public ImporterException(String message, Throwable cause) {
		this(message, cause, false);
	}

	public ImporterException(String message, Throwable cause, boolean rejected) {
		super(message, cause);
		this.rejected = rejected;
	}

	public boolean isRejected() {
		return rejected;
	}

}
