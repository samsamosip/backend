package com.youthbenefit.ontong;

/** 수집 결과: 받은 건수, 새로 저장, 내용이 바뀌어 갱신, 그대로. */
public record OntongSyncResult(int fetched, int created, int updated, int unchanged) {
}
