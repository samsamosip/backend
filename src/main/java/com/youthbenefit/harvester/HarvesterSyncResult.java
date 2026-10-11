package com.youthbenefit.harvester;

/**
 * 수집 결과: 목록 건수, 오래돼서 건너뜀, 상세를 받은 건수, 새로 저장, 내용이 바뀌어 갱신, 그대로, 상세 받기 실패.
 */
public record HarvesterSyncResult(int listed, int skippedOld, int fetched, int created, int updated, int unchanged,
		int failed) {
}
