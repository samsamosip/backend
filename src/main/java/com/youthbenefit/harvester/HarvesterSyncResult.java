package com.youthbenefit.harvester;

/**
 * 수집 결과. mode 는 FULL(목록 전체 비교) 또는 CHANGES(변경 목록만). seen 은 FULL 이면 목록 건수, CHANGES 면 변경 건수.
 * 나머지: 오래돼서 건너뜀, 상세를 받은 건수, 새로 저장, 내용이 바뀌어 갱신, 그대로, 출처에서 사라짐, 상세 받기 실패.
 */
public record HarvesterSyncResult(Mode mode, int seen, int skippedOld, int fetched, int created, int updated,
		int unchanged, int removed, int failed) {

	public enum Mode {
		FULL, CHANGES
	}

}
