package com.youthbenefit.importer;

/** 불러오기 서버 호출 실패 (설정 없음, 키 오류, 연결 실패 등). */
public class ImporterException extends RuntimeException {

	public ImporterException(String message) {
		super(message);
	}

	public ImporterException(String message, Throwable cause) {
		super(message, cause);
	}

}
