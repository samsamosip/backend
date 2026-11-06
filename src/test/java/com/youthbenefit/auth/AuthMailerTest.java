package com.youthbenefit.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.youthbenefit.common.ApiException;
import org.springframework.http.HttpStatus;

import org.junit.jupiter.api.Test;

class AuthMailerTest {

	@Test
	void stopsBeforeGmailDailyLimit() {
		AuthMailer noMail = new AuthMailer(new AuthProperties(null, 30, 14, null, null, false), 1);
		// 메일 설정이 없으면(로컬) 한도를 보지 않는다
		noMail.checkCapacity();
	}

	@Test
	void mailServerFailureIsFriendly503() {
		// 메일 통로가 막힌 서버처럼: 아무도 안 받는 주소로 보내면 바로 실패
		AuthMailer blocked = new AuthMailer(new AuthProperties(null, 30, 14, null,
				new AuthProperties.Mail("127.0.0.1", 1, "sender@example.com", "x", "청년혜택 길잡이", null), true), 10);
		assertThat(blocked.configured()).isTrue();
		assertThatThrownBy(() -> blocked.sendCode("someone@example.com", VerificationCode.Purpose.SIGNUP, "123456"))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
				assertThat(ex.code()).isEqualTo("MAIL_FAILED");
				assertThat(ex.getMessage()).isEqualTo("인증 메일을 보내지 못했어요. 잠시 후 다시 해 주세요.");
			});
	}

	@Test
	void fromAddressIsMailFromOrUsername() {
		// Gmail: 로그인 아이디가 곧 보내는 주소
		assertThat(new AuthMailer(new AuthProperties(null, 30, 14, null,
				new AuthProperties.Mail("smtp.gmail.com", 587, "sender@example.com", "x", "청년혜택 길잡이", null), false), 10)
			.fromAddress()).isEqualTo("sender@example.com");
		// Postmark: 아이디·비밀번호는 Server API 토큰, 보내는 주소는 따로
		assertThat(new AuthMailer(new AuthProperties(null, 30, 14, null, new AuthProperties.Mail("smtp.postmarkapp.com",
				587, "token-1234", "token-1234", "청년혜택 길잡이", " no-reply@example.com "), false), 10).fromAddress())
			.isEqualTo("no-reply@example.com");
	}

	@Test
	void mailHasCodeAndGuidance() {
		assertThat(AuthMailer.text(true, "482193")).contains("인증 코드: 482193", "10분", "회원가입을 마치려면", "보내기 전용");
		assertThat(AuthMailer.text(false, "482193")).contains("비밀번호를 새로 설정하려면");
		assertThat(AuthMailer.html(true, "482193")).contains(">482193<", "이메일 인증 코드", "width=\"100%\"")
			.doesNotContain("%s");
	}

}
