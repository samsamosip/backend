package com.youthbenefit.notification;

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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알림 하나: 준비함에 담은 공고가 바뀌었다. readAt 이 null 이면 안 읽음. */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID anonymousId;

	private Long policyId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "policy_change_id")
	private PolicyChange policyChange;

	private LocalDateTime createdAt;

	private LocalDateTime readAt;

	Notification(UUID anonymousId, Long policyId, PolicyChange policyChange) {
		this.anonymousId = anonymousId;
		this.policyId = policyId;
		this.policyChange = policyChange;
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
