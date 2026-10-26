package com.youthbenefit.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 메일로 보낸 6자리 코드 한 건. 코드는 SHA-256 만 저장. 비밀번호 찾기는 코드가 맞으면 재설정 통행증(ticket)을 단다. */
@Entity
@Table(name = "verification_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationCode {

	enum Purpose {
		SIGNUP, RESET
	}

	static final int MAX_ATTEMPTS = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String email;

	@Enumerated(EnumType.STRING)
	private Purpose purpose;

	private String codeHash;

	private LocalDateTime expiresAt;

	private int attempts;

	private LocalDateTime consumedAt;

	private String ticketHash;

	private LocalDateTime ticketExpiresAt;

	private LocalDateTime createdAt;

	VerificationCode(String email, Purpose purpose, String codeHash, LocalDateTime expiresAt) {
		this.email = email;
		this.purpose = purpose;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
	}

	boolean isUsable(LocalDateTime now) {
		return consumedAt == null && attempts < MAX_ATTEMPTS && expiresAt.isAfter(now);
	}

	void recordWrongAttempt() {
		attempts++;
	}

	void consume(LocalDateTime now) {
		consumedAt = now;
	}

	void attachTicket(String ticketHash, LocalDateTime ticketExpiresAt) {
		this.ticketHash = ticketHash;
		this.ticketExpiresAt = ticketExpiresAt;
	}

	void useTicket() {
		ticketHash = null;
		ticketExpiresAt = null;
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

}
