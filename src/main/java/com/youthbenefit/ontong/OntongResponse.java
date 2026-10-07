package com.youthbenefit.ontong;

import java.util.List;
import java.util.Map;

/**
 * 온통청년 청년정책 API 응답. 정책 한 건은 필드 60개를 원본 그대로 보존하려고 Map 으로 받는다
 * (raw_payload 저장과 변경 감지 해시에 쓴다).
 */
public record OntongResponse(int resultCode, String resultMessage, Result result) {

	public record Result(Paging pagging, List<Map<String, Object>> youthPolicyList) {
	}

	public record Paging(int totCount, int pageNum, int pageSize) {
	}

}
