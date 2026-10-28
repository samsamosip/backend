package com.youthbenefit.auth;

import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 인증 코드 메일. MAIL_USERNAME 이 없으면(로컬 개발) 메일 대신 서버 로그에 코드를 남긴다.
 * Gmail 이면 MAIL_USERNAME = 보내는 주소, MAIL_PASSWORD = 그 계정의 "앱 비밀번호".
 */
@Slf4j
@Component
public class AuthMailer {

	private final JavaMailSenderImpl sender;

	private final String address;

	private final String fromName;

	public AuthMailer(AuthProperties properties) {
		AuthProperties.Mail mail = properties.mail();
		if (mail == null || mail.username() == null || mail.username().isBlank()) {
			this.sender = null;
			this.address = null;
			this.fromName = null;
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
		this.address = mail.username();
		this.fromName = mail.fromName();
	}

	public void sendCode(String email, VerificationCode.Purpose purpose, String code) {
		if (sender == null) {
			log.warn("[개발용 — 메일 설정 없음] {} 코드 {} → {}", purpose, code, email);
			return;
		}
		boolean signup = purpose == VerificationCode.Purpose.SIGNUP;
		String subject = signup ? "[청년혜택 길잡이] 회원가입 인증 코드 " + code : "[청년혜택 길잡이] 비밀번호 재설정 코드 " + code;
		try {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(address, fromName);
			helper.setTo(email);
			helper.setSubject(subject);
			// 글자만 보는 메일 앱용과 보기 좋은 화면용을 함께 (글만 있는 짧은 메일은 스팸으로 가기 쉽다)
			helper.setText(text(signup, code), html(signup, code));
			sender.send(message);
		}
		catch (MessagingException | java.io.UnsupportedEncodingException ex) {
			throw new IllegalStateException("메일을 만들지 못했어요", ex);
		}
		// 보낸 기록 (주소는 앞 두 글자만)
		log.info("{} 코드 메일 보냄 → {}***{}", purpose, email.substring(0, Math.min(2, email.indexOf('@'))),
				email.substring(email.indexOf('@')));
	}

	static String text(boolean signup, String code) {
		return "안녕하세요, 청년혜택 길잡이입니다.\n\n" + (signup ? "회원가입을 마치려면" : "비밀번호를 새로 설정하려면")
				+ " 아래 인증 코드를 앱 화면에 입력해 주세요.\n\n인증 코드: " + code
				+ "\n\n코드는 10분 동안 쓸 수 있어요. 코드를 다시 받으면 이전 코드는 쓸 수 없어요.\n"
				+ "요청한 적이 없다면 이 메일은 무시해 주세요. 계정에는 아무 변화가 없어요.\n\n"
				+ "이 메일은 보내기 전용이라 답장을 받지 않아요.\n청년혜택 길잡이 — 나에게 맞는 청년 혜택을 놓치지 않게";
	}

	static String html(boolean signup, String code) {
		String action = signup ? "회원가입을 마치려면" : "비밀번호를 새로 설정하려면";
		return """
				<!doctype html><html lang="ko"><body style="margin:0;padding:0;background:#f3f6fb;font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#1f2937">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="padding:32px 16px"><tr><td align="center">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;padding:32px 28px">
				<tr><td style="font-size:15px;font-weight:700;color:#2f5bd3">청년혜택 길잡이</td></tr>
				<tr><td style="padding-top:20px;font-size:20px;font-weight:700">%s</td></tr>
				<tr><td style="padding-top:8px;font-size:14px;line-height:1.6;color:#4b5563">%s 아래 인증 코드를 앱 화면에 입력해 주세요.</td></tr>
				<tr><td align="center" style="padding:24px 0">
				<div style="display:inline-block;background:#eef3ff;border-radius:12px;padding:16px 28px;font-size:32px;font-weight:700;letter-spacing:8px;color:#1f3fa8">%s</div>
				</td></tr>
				<tr><td style="font-size:13px;line-height:1.7;color:#6b7280">
				코드는 <b>10분</b> 동안 쓸 수 있어요. 코드를 다시 받으면 이전 코드는 쓸 수 없어요.<br>
				요청한 적이 없다면 이 메일은 무시해 주세요. 계정에는 아무 변화가 없어요.
				</td></tr>
				<tr><td style="padding-top:24px;border-top:1px solid #e5e7eb;font-size:12px;line-height:1.6;color:#9ca3af">
				이 메일은 보내기 전용이라 답장을 받지 않아요.<br>청년혜택 길잡이 — 나에게 맞는 청년 혜택을 놓치지 않게
				</td></tr>
				</table></td></tr></table></body></html>
				""".formatted(signup ? "이메일 인증 코드" : "비밀번호 재설정 코드", action, code);
	}

}
