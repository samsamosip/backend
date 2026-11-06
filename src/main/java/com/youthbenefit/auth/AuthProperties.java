package com.youthbenefit.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 app.auth.*. jwtSecret 이 비어 있으면 실행할 때마다 임시 키를 만든다(로컬 개발용, 재시작하면 로그인 풀림).
 * kakao.redirectUris 는 카카오 개발자 콘솔에 등록한 주소와 같아야 한다.
 *
 * @param devShowCode 개발용 서버에서만: 메일 설정이 없을 때 인증 코드를 응답(devCode)에 같이 내려 준다. 메일 설정이 있으면 무시한다
 */
@ConfigurationProperties("app.auth")
public record AuthProperties(String jwtSecret, int accessTokenMinutes, int refreshTokenDays, Kakao kakao, Mail mail,
		boolean devShowCode) {

	public record Kakao(String clientId, String clientSecret, List<String> redirectUris) {
	}

	/**
	 * username 이 비어 있으면 메일 대신 서버 로그에 코드를 남긴다. from 은 보내는 주소 — 비어 있으면 username 을 쓴다(Gmail 처럼
	 * 로그인 아이디가 메일 주소일 때). Postmark 처럼 아이디가 토큰이면 from 에 인증된 보내는 주소를 넣는다.
	 */
	public record Mail(String host, int port, String username, String password, String fromName, String from) {

		public Mail(String host, int port, String username, String password, String fromName) {
			this(host, port, username, password, fromName, null);
		}

	}

}
