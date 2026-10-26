package com.youthbenefit.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계정. 프로필·준비함·알림은 ownerId 로 묶는다(로그인 전 임시 ID 와 같은 칸). 이메일 계정과 카카오 계정이 있고,
 * 카카오만 쓰는 계정은 email·passwordHash 가 없다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	static final int MAX_FAILED_LOGINS = 5;

	static final int LOCK_MINUTES = 15;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID ownerId;

	private String email;

	private String passwordHash;

	private boolean emailVerified;

	private String nickname;

	private Long kakaoId;

	private int failedLoginCount;

	private LocalDateTime lockedUntil;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	static User withEmail(String email, String passwordHash, String nickname) {
		User u = new User();
		u.ownerId = UUID.randomUUID();
		u.email = email;
		u.passwordHash = passwordHash;
		u.nickname = nickname;
		return u;
	}

	static User withKakao(long kakaoId, String nickname) {
		User u = new User();
		u.ownerId = UUID.randomUUID();
		u.kakaoId = kakaoId;
		u.nickname = nickname;
		u.emailVerified = false;
		return u;
	}

	/** 아직 인증 전인 가입을 다시 하면 비밀번호와 닉네임을 새 값으로 */
	void restartSignup(String passwordHash, String nickname) {
		this.passwordHash = passwordHash;
		this.nickname = nickname;
	}

	void verifyEmail() {
		emailVerified = true;
	}

	boolean isLocked(LocalDateTime now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	/** 비밀번호가 틀렸다. 5번째면 15분 잠그고 횟수를 0으로. 잠겼으면 true. */
	boolean recordFailedLogin(LocalDateTime now) {
		failedLoginCount++;
		if (failedLoginCount >= MAX_FAILED_LOGINS) {
			failedLoginCount = 0;
			lockedUntil = now.plusMinutes(LOCK_MINUTES);
			return true;
		}
		return false;
	}

	int remainingAttempts() {
		return MAX_FAILED_LOGINS - failedLoginCount;
	}

	void recordSuccessfulLogin() {
		failedLoginCount = 0;
		lockedUntil = null;
	}

	void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
		recordSuccessfulLogin();
	}

	void rename(String nickname) {
		this.nickname = nickname;
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

}
