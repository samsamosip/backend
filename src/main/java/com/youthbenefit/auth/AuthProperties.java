package com.youthbenefit.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.auth.*. jwtSecret 이 비어 있으면 실행할 때마다 임시 키를 만든다(로컬 개발용, 재시작하면 로그인 풀림).
 * kakao.redirectUris 는 카카오 개발자 콘솔에 등록한 주소와 같아야 한다.
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(String jwtSecret, int accessTokenMinutes, int refreshTokenDays, Kakao kakao, Mail mail) {

	public record Kakao(String clientId, String clientSecret, List<String> redirectUris) {
	}

	/** username 이 비어 있으면 메일 대신 서버 로그에 코드를 남긴다. */
	public record Mail(String host, int port, String username, String password, String fromName) {
	}

}
