package com.youthbenefit.auth;

import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * 인증 코드 메일. MAIL_USERNAME 이 없으면(로컬 개발) 메일 대신 서버 로그에 코드를 남긴다.
 * Gmail 이면 MAIL_USERNAME = 보내는 주소, MAIL_PASSWORD = 그 계정의 "앱 비밀번호".
 */
@Slf4j
@Component
public class AuthMailer {

	private final JavaMailSenderImpl sender;

	private final String from;

	public AuthMailer(AuthProperties properties) {
		AuthProperties.Mail mail = properties.mail();
		if (mail == null || mail.username() == null || mail.username().isBlank()) {
			this.sender = null;
			this.from = null;
			return;
		}
		JavaMailSenderImpl s = new JavaMailSenderImpl();
		s.setHost(mail.host());
		s.setPort(mail.port());
		s.setUsername(mail.username());
		s.setPassword(mail.password());
		s.setDefaultEncoding("UTF-8");
		Properties props = s.getJavaMailProperties();
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.connectiontimeout", "10000");
		props.put("mail.smtp.timeout", "10000");
		this.sender = s;
		this.from = mail.fromName() + " <" + mail.username() + ">";
	}

	public void sendCode(String email, VerificationCode.Purpose purpose, String code) {
		String subject = purpose == VerificationCode.Purpose.SIGNUP ? "[청년혜택 길잡이] 이메일 인증 코드"
				: "[청년혜택 길잡이] 비밀번호 재설정 코드";
		String body = "인증 코드: " + code + "\n\n10분 동안 쓸 수 있어요. 요청한 적이 없다면 이 메일은 무시해 주세요.";
		if (sender == null) {
			log.warn("[개발용 — 메일 설정 없음] {} 코드 {} → {}", purpose, code, email);
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject(subject);
		message.setText(body);
		sender.send(message);
	}

}
