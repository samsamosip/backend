package com.youthbenefit.auth;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 유지용 갱신 토큰. 원문은 사용자에게만 있고 여기엔 SHA-256 만 남는다. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	private String tokenHash;

	private LocalDateTime expiresAt;

	private LocalDateTime revokedAt;

	private LocalDateTime createdAt;

	RefreshToken(User user, String tokenHash, LocalDateTime expiresAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	boolean isRevoked() {
		return revokedAt != null;
	}

	void revoke(LocalDateTime now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

}
