package com.youthbenefit.auth;

import com.youthbenefit.common.ApiException;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * 카카오 로그인. 프론트가 카카오에서 받은 인가 코드를 카카오 토큰으로 바꾸고, 그 토큰으로 회원 번호와 닉네임을 읽는다.
 * 카카오 토큰은 여기서만 쓰고 저장하지 않는다.
 */
@Component
public class KakaoOAuthClient {

	public record KakaoUser(long id, String nickname) {
	}

	private final AuthProperties.Kakao kakao;

	private final RestClient restClient;

	public KakaoOAuthClient(AuthProperties properties, RestClient.Builder builder) {
		this.kakao = properties.kakao();
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(10));
		this.restClient = builder.clone().requestFactory(factory).build();
	}

	public KakaoUser exchange(String code, String redirectUri) {
		if (kakao == null || kakao.clientId() == null || kakao.clientId().isBlank()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KAKAO_NOT_CONFIGURED",
					"카카오 로그인이 아직 설정되지 않았어요.");
		}
		try {
			MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
			form.add("grant_type", "authorization_code");
			form.add("client_id", kakao.clientId());
			form.add("redirect_uri", redirectUri);
			form.add("code", code);
			if (kakao.clientSecret() != null && !kakao.clientSecret().isBlank()) {
				form.add("client_secret", kakao.clientSecret());
			}
			JsonNode token = restClient.post()
				.uri("https://kauth.kakao.com/oauth/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(JsonNode.class);
			String accessToken = token == null ? null : token.path("access_token").asString(null);
			if (accessToken == null) {
				throw failed();
			}
			JsonNode me = restClient.get()
				.uri("https://kapi.kakao.com/v2/user/me")
				.header("Authorization", "Bearer " + accessToken)
				.retrieve()
				.body(JsonNode.class);
			if (me == null || !me.path("id").isNumber()) {
				throw failed();
			}
			String nickname = me.path("kakao_account").path("profile").path("nickname").asString(null);
			if (nickname == null) {
				nickname = me.path("properties").path("nickname").asString(null);
			}
			return new KakaoUser(me.path("id").longValue(), nickname);
		}
		catch (RestClientException ex) {
			throw failed();
		}
	}

	private static ApiException failed() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "KAKAO_LOGIN_FAILED", "카카오 로그인에 실패했어요. 다시 시도해 주세요.");
	}

}
