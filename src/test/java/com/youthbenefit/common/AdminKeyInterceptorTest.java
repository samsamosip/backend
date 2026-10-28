package com.youthbenefit.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 관리자 키를 설정하지 않았으면(로컬) 서버 자신에서 온 요청만 통과 — 배포에서 키를 잊어도 밖에서는 못 부른다 */
class AdminKeyInterceptorTest {

	private final AdminKeyInterceptor noKey = new AdminKeyInterceptor("", JsonMapper.builder().build());

	@Test
	void withoutKeyOnlyLocalCallsPass() throws Exception {
		assertThat(noKey.preHandle(request("127.0.0.1"), new MockHttpServletResponse(), null)).isTrue();
		assertThat(noKey.preHandle(request("0:0:0:0:0:0:0:1"), new MockHttpServletResponse(), null)).isTrue();
		MockHttpServletResponse response = new MockHttpServletResponse();
		assertThat(noKey.preHandle(request("203.0.113.7"), response, null)).isFalse();
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("ADMIN_API_KEY");
	}

	private static MockHttpServletRequest request(String ip) {
		MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/admin/ontong/sync");
		r.setRemoteAddr(ip);
		return r;
	}

}
