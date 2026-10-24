package com.youthbenefit.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 알림 하나. readAt 이 null 이면 안 읽음. type 마다 채우는 칸이 다르다: CHANGE 는 policyChange, NEW_MATCHES 는 payload(새 공고 목록),
 * DEADLINE 은 policyId + payload(마감일·남은 날). dedupeKey 는 같은 알림을 두 번 만들지 않기 위한 키.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID anonymousId;

	@Enumerated(EnumType.STRING)
	private NotificationType type;

	private Long policyId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "policy_change_id")
	private PolicyChange policyChange;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String payload;

	private String dedupeKey;

	private LocalDateTime createdAt;

	private LocalDateTime readAt;

	static Notification change(UUID owner, Long policyId, PolicyChange change) {
		Notification n = new Notification();
		n.anonymousId = owner;
		n.type = NotificationType.CHANGE;
		n.policyId = policyId;
		n.policyChange = change;
		return n;
	}

	static Notification of(UUID owner, NotificationType type, Long policyId, String payload, String dedupeKey) {
		Notification n = new Notification();
		n.anonymousId = owner;
		n.type = type;
		n.policyId = policyId;
		n.payload = payload;
		n.dedupeKey = dedupeKey;
		return n;
	}

	void markRead() {
		if (readAt == null) {
			readAt = LocalDateTime.now();
		}
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

}
