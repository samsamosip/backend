package com.youthbenefit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthMailerTest {

	@Test
	void mailHasCodeAndGuidance() {
		assertThat(AuthMailer.text(true, "482193")).contains("인증 코드: 482193", "10분", "회원가입을 마치려면", "보내기 전용");
		assertThat(AuthMailer.text(false, "482193")).contains("비밀번호를 새로 설정하려면");
		assertThat(AuthMailer.html(true, "482193")).contains(">482193<", "이메일 인증 코드", "width=\"100%\"")
			.doesNotContain("%s");
	}

}
