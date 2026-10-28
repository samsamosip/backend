package com.youthbenefit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.auth.VerificationCode.Purpose;
import com.youthbenefit.common.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "spring.docker.compose.enabled=false",
		"app.auth.kakao.redirect-uris=http://localhost:5173/auth/kakao/callback" })
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class AuthApiIntegrationTest {

	private static final String CALLBACK = "http://localhost:5173/auth/kakao/callback";

	@MockitoBean
	AuthMailer mailer;

	@MockitoBean
	KakaoOAuthClient kakaoClient;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		jdbc.execute("delete from notifications; delete from notification_settings; delete from checklist_items; "
				+ "delete from applications; delete from user_profiles; delete from refresh_tokens; "
				+ "delete from verification_codes; delete from users");
	}

	// ── 이메일 가입 ───────────────────────────────────────

	@Test
	void signupVerifyThenLoginWithSpecialCharacterPassword() throws Exception {
		signup("Jiyun@Example.com", "pass!@#word1", "지윤").andExpect(status().isAccepted())
			.andExpect(jsonPath("$.email").value("jiyun@example.com"))
			.andExpect(jsonPath("$.expiresInSeconds").value(600))
			.andExpect(jsonPath("$.resendAfterSeconds").value(60));

		// 인증 전 로그인 → 403
		login("jiyun@example.com", "pass!@#word1").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

		String code = lastCode("jiyun@example.com", Purpose.SIGNUP);
		String wrong = code.equals("000000") ? "111111" : "000000";
		json(post("/api/v1/auth/email/verify"), "{\"email\":\"jiyun@example.com\",\"code\":\"" + wrong + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CODE_MISMATCH"))
			.andExpect(jsonPath("$.message").value("코드가 맞지 않아요. (남은 횟수 4번)"));

		String access = token(json(post("/api/v1/auth/email/verify"),
				"{\"email\":\"jiyun@example.com\",\"code\":\"" + code + "\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.nickname").value("지윤"))
			.andExpect(jsonPath("$.account.emailVerified").value(true))
			.andExpect(jsonPath("$.account.hasPassword").value(true))
			.andExpect(jsonPath("$.account.kakaoLinked").value(false))
			.andExpect(jsonPath("$.accessTokenExpiresIn").value(1800)), "accessToken");

		mockMvc.perform(get("/api/v1/me/account").header("Authorization", "Bearer " + access))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("jiyun@example.com"));

		// 같은 코드는 한 번만
		json(post("/api/v1/auth/email/verify"), "{\"email\":\"jiyun@example.com\",\"code\":\"" + code + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CODE_EXPIRED"));

		login("JIYUN@example.com ", "pass!@#word1").andExpect(status().isOk())
			.andExpect(jsonPath("$.refreshToken").isNotEmpty());
		signup("jiyun@example.com", "another1pass", "다른사람").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	void passwordRulesAndInputErrors() throws Exception {
		signup("a@b.com", "short1", "엑스").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password").exists());
		signup("a@b.com", "onlyletters", "엑스").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password").exists());
		signup("a@b.com", "12345678", "엑스").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password").exists());
		signup("not-an-email", "good1pass", "엑스").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email").exists());
		signup("a@b.com", "good1pass", "").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.nickname").exists());
		verify(mailer, never()).sendCode(anyString(), Mockito.any(), anyString());
	}

	@Test
	void codeResendHasCooldownAndFiveWrongCodesInvalidate() throws Exception {
		signup("cool@example.com", "good1pass", "쿨쿨").andExpect(status().isAccepted());
		json(post("/api/v1/auth/email/resend"), "{\"email\":\"cool@example.com\"}")
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("CODE_COOLDOWN"));

		String code = lastCode("cool@example.com", Purpose.SIGNUP);
		String wrong = code.equals("000000") ? "111111" : "000000";
		for (int i = 0; i < 4; i++) {
			json(post("/api/v1/auth/email/verify"), "{\"email\":\"cool@example.com\",\"code\":\"" + wrong + "\"}")
				.andExpect(jsonPath("$.code").value("CODE_MISMATCH"));
		}
		json(post("/api/v1/auth/email/verify"), "{\"email\":\"cool@example.com\",\"code\":\"" + wrong + "\"}")
			.andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
		// 맞는 코드여도 이미 무효
		json(post("/api/v1/auth/email/verify"), "{\"email\":\"cool@example.com\",\"code\":\"" + code + "\"}")
			.andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
	}

	// ── 로그인 ───────────────────────────────────────────

	@Test
	void fiveWrongPasswordsLockTheAccount() throws Exception {
		verifiedUser("lock@example.com", "good1pass");
		login("nobody@example.com", "good1pass").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
		for (int left = 4; left >= 1; left--) {
			login("lock@example.com", "wrong1pass").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 맞지 않아요. (남은 횟수 " + left + "번)"));
		}
		login("lock@example.com", "wrong1pass").andExpect(status().isLocked())
			.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
		login("lock@example.com", "good1pass").andExpect(status().isLocked())
			// 화면(08-b) "15:57 이후", "14분 32초 후"용
			.andExpect(jsonPath("$.details.lockedUntil").isNotEmpty())
			.andExpect(jsonPath("$.details.retryAfterSeconds").value(org.hamcrest.Matchers.greaterThan(800)))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("이후에 다시 시도")));
	}

	@Test
	void nicknameIsTwoToTenCharacters() throws Exception {
		signup("n1@example.com", "good1pass", "가").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.nickname").value("닉네임은 2~10자로 써 주세요"));
		signup("n2@example.com", "good1pass", "열한글자닉네임입니다요").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.nickname").exists());
		signup("n3@example.com", "good1pass", "홍길동").andExpect(status().isAccepted());
	}

	@Test
	void refreshTokenRotatesAndReuseLogsOutEverywhere() throws Exception {
		String first = verifiedUser("rot@example.com", "good1pass");
		String body = json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + first + "\"}")
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String second = JsonPath.read(body, "$.refreshToken");
		assertThat(second).isNotEqualTo(first);

		// 이미 바꾼 토큰을 다시 쓰면 → 모두 끊김(second 도)
		json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + first + "\"}")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_REUSED"));
		json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + second + "\"}")
			.andExpect(status().isUnauthorized());
		json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"made-up\"}").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
	}

	@Test
	void logoutRevokesRefreshToken() throws Exception {
		String refresh = verifiedUser("out@example.com", "good1pass");
		json(post("/api/v1/auth/logout"), "{\"refreshToken\":\"" + refresh + "\"}").andExpect(status().isNoContent());
		json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + refresh + "\"}")
			.andExpect(status().isUnauthorized());
	}

	@Test
	void badBearerTokenIsRejectedNotTreatedAsAnonymous() throws Exception {
		mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer nonsense")
			.header("X-Anonymous-Id", UUID.randomUUID().toString()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
		mockMvc.perform(get("/api/v1/me/account")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
		mockMvc.perform(get("/api/v1/me/profile")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("X-Anonymous-Id 헤더에 UUID 를 넣어 주세요."));
		mockMvc.perform(get("/api/v1/me/profile").header("X-Anonymous-Id", "not-a-uuid"))
			.andExpect(status().isBadRequest());
	}

	// ── 비밀번호 찾기 ─────────────────────────────────────

	@Test
	void resetPasswordInThreeSteps() throws Exception {
		String oldRefresh = verifiedUser("reset@example.com", "old1pass");

		json(post("/api/v1/auth/password/forgot"), "{\"email\":\"reset@example.com\"}")
			.andExpect(status().isAccepted());
		// 없는 이메일도 같은 응답, 메일은 안 보냄
		json(post("/api/v1/auth/password/forgot"), "{\"email\":\"ghost@example.com\"}")
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.email").value("ghost@example.com"));
		verify(mailer, never()).sendCode(eq("ghost@example.com"), Mockito.any(), anyString());

		String code = lastCode("reset@example.com", Purpose.RESET);
		String ticket = JsonPath.read(json(post("/api/v1/auth/password/verify-code"),
				"{\"email\":\"reset@example.com\",\"code\":\"" + code + "\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresInSeconds").value(600))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.resetToken");

		json(post("/api/v1/auth/password/reset"), "{\"resetToken\":\"" + ticket + "\",\"newPassword\":\"weak\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.newPassword").exists());
		json(post("/api/v1/auth/password/reset"),
				"{\"resetToken\":\"" + ticket + "\",\"newPassword\":\"new1pass!\"}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty());

		// 통행증은 한 번만, 예전 로그인은 끊김, 새 비밀번호로만 로그인
		json(post("/api/v1/auth/password/reset"),
				"{\"resetToken\":\"" + ticket + "\",\"newPassword\":\"again1pass\"}")
			.andExpect(jsonPath("$.code").value("RESET_EXPIRED"));
		json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + oldRefresh + "\"}")
			.andExpect(status().isUnauthorized());
		login("reset@example.com", "old1pass").andExpect(status().isUnauthorized());
		login("reset@example.com", "new1pass!").andExpect(status().isOk());
	}

	// ── 카카오 ───────────────────────────────────────────

	@Test
	void kakaoLoginCreatesAccountOnceAndHasNoPassword() throws Exception {
		given(kakaoClient.exchange("kakao-code", CALLBACK)).willReturn(new KakaoOAuthClient.KakaoUser(4242L, "카톡지윤"));

		String body = kakao("kakao-code", CALLBACK).andExpect(status().isOk())
			.andExpect(jsonPath("$.account.nickname").value("카톡지윤"))
			.andExpect(jsonPath("$.account.kakaoLinked").value(true))
			.andExpect(jsonPath("$.account.hasPassword").value(false))
			.andExpect(jsonPath("$.account.email").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Number id = JsonPath.read(body, "$.account.id");
		kakao("kakao-code", CALLBACK).andExpect(jsonPath("$.account.id").value(id));
		assertThat(users.count()).isEqualTo(1);

		String access = JsonPath.read(body, "$.accessToken");
		json(post("/api/v1/me/account/password").header("Authorization", "Bearer " + access),
				"{\"currentPassword\":\"x\",\"newPassword\":\"new1pass\"}")
			.andExpect(jsonPath("$.code").value("NO_PASSWORD"));
	}

	@Test
	void kakaoRejectsUnknownRedirectAndReportsKakaoFailure() throws Exception {
		kakao("c", "https://evil.example.com/cb").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.redirectUri").exists());
		given(kakaoClient.exchange("expired", CALLBACK))
			.willThrow(new ApiException(HttpStatus.UNAUTHORIZED, "KAKAO_LOGIN_FAILED", "카카오 로그인에 실패했어요."));
		kakao("expired", CALLBACK).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("KAKAO_LOGIN_FAILED"));
	}

	@Test
	void kakaoWithoutNicknameGetsDefault() throws Exception {
		given(kakaoClient.exchange("c", CALLBACK)).willReturn(new KakaoOAuthClient.KakaoUser(7L, null));
		kakao("c", CALLBACK).andExpect(jsonPath("$.account.nickname").value("카카오 사용자"));
	}

	// ── 로그인 전 데이터 옮기기 ────────────────────────────

	@Test
	void anonymousProfileMovesToAccountOnLogin() throws Exception {
		UUID anon = UUID.randomUUID();
		json(put("/api/v1/me/profile").header("X-Anonymous-Id", anon.toString()), "{\"birthDate\":\"2001-03-04\"}")
			.andExpect(status().isOk());

		given(kakaoClient.exchange("c", CALLBACK)).willReturn(new KakaoOAuthClient.KakaoUser(99L, "옮김"));
		String access = token(json(post("/api/v1/auth/kakao").header("X-Anonymous-Id", anon.toString()),
				"{\"code\":\"c\",\"redirectUri\":\"" + CALLBACK + "\"}"), "accessToken");

		mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + access))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.birthDate").value("2001-03-04"));
		mockMvc.perform(get("/api/v1/me/profile").header("X-Anonymous-Id", anon.toString()))
			.andExpect(status().isNotFound());

		// 계정 UUID 를 헤더로 보내도 계정 데이터에 못 들어온다
		UUID ownerId = users.findByKakaoId(99L).orElseThrow().getOwnerId();
		mockMvc.perform(get("/api/v1/me/profile").header("X-Anonymous-Id", ownerId.toString()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
		// 로그인 때 남의 계정 UUID 를 보내도 옮겨지지 않는다
		given(kakaoClient.exchange("other", CALLBACK)).willReturn(new KakaoOAuthClient.KakaoUser(100L, "남"));
		String otherAccess = token(json(post("/api/v1/auth/kakao").header("X-Anonymous-Id", ownerId.toString()),
				"{\"code\":\"other\",\"redirectUri\":\"" + CALLBACK + "\"}"), "accessToken");
		mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + otherAccess))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + access))
			.andExpect(status().isOk());
	}

	@Test
	void accountKeepsItsOwnProfileWhenAnonymousAlsoHasOne() throws Exception {
		given(kakaoClient.exchange("c", CALLBACK)).willReturn(new KakaoOAuthClient.KakaoUser(5L, "기존"));
		String access = token(kakao("c", CALLBACK), "accessToken");
		json(put("/api/v1/me/profile").header("Authorization", "Bearer " + access), "{\"birthDate\":\"1999-01-01\"}")
			.andExpect(status().isOk());

		UUID anon = UUID.randomUUID();
		json(put("/api/v1/me/profile").header("X-Anonymous-Id", anon.toString()), "{\"birthDate\":\"2005-05-05\"}");
		String again = token(json(post("/api/v1/auth/kakao").header("X-Anonymous-Id", anon.toString()),
				"{\"code\":\"c\",\"redirectUri\":\"" + CALLBACK + "\"}"), "accessToken");

		mockMvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer " + again))
			.andExpect(jsonPath("$.birthDate").value("1999-01-01"));
		mockMvc.perform(get("/api/v1/me/profile").header("X-Anonymous-Id", anon.toString()))
			.andExpect(status().isNotFound());
	}

	// ── 내 계정 ──────────────────────────────────────────

	@Test
	void renameChangePasswordAndDeleteAccount() throws Exception {
		verifiedUser("me@example.com", "good1pass");
		String access = token(login("me@example.com", "good1pass"), "accessToken");

		json(patch("/api/v1/me/account").header("Authorization", "Bearer " + access), "{\"nickname\":\" 새이름 \"}")
			.andExpect(jsonPath("$.nickname").value("새이름"));

		json(post("/api/v1/me/account/password").header("Authorization", "Bearer " + access),
				"{\"currentPassword\":\"wrong1pass\",\"newPassword\":\"next1pass\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.currentPassword").exists());
		json(post("/api/v1/me/account/password").header("Authorization", "Bearer " + access),
				"{\"currentPassword\":\"good1pass\",\"newPassword\":\"next1pass\"}")
			.andExpect(status().isNoContent());
		login("me@example.com", "next1pass").andExpect(status().isOk());

		json(put("/api/v1/me/profile").header("Authorization", "Bearer " + access), "{\"birthDate\":\"2000-01-01\"}");
		mockMvc.perform(delete("/api/v1/me/account").header("Authorization", "Bearer " + access))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/me/account").header("Authorization", "Bearer " + access))
			.andExpect(status().isUnauthorized());
		assertThat(users.count()).isZero();
		assertThat(jdbc.queryForObject("select count(*) from user_profiles", Integer.class)).isZero();
		// 같은 이메일로 다시 가입 가능
		signup("me@example.com", "good1pass", "다시").andExpect(status().isAccepted());
	}

	// ── 도우미 ───────────────────────────────────────────

	/** 가입+인증까지 마친 계정. 갱신 토큰을 돌려준다. */
	private String verifiedUser(String email, String password) throws Exception {
		signup(email, password, "테스트").andExpect(status().isAccepted());
		return token(json(post("/api/v1/auth/email/verify"),
				"{\"email\":\"" + email + "\",\"code\":\"" + lastCode(email, Purpose.SIGNUP) + "\"}")
			.andExpect(status().isOk()), "refreshToken");
	}

	private String lastCode(String email, Purpose purpose) {
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(mailer, atLeastOnce()).sendCode(eq(email), eq(purpose), code.capture());
		return code.getValue();
	}

	private ResultActions signup(String email, String password, String nickname) throws Exception {
		return json(post("/api/v1/auth/signup"),
				"{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"nickname\":\"" + nickname + "\"}");
	}

	private ResultActions login(String email, String password) throws Exception {
		return json(post("/api/v1/auth/login"), "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	private ResultActions kakao(String code, String redirectUri) throws Exception {
		return json(post("/api/v1/auth/kakao"), "{\"code\":\"" + code + "\",\"redirectUri\":\"" + redirectUri + "\"}");
	}

	private ResultActions json(MockHttpServletRequestBuilder request, String body) throws Exception {
		return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private static String token(ResultActions result, String field) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$." + field);
	}

}
