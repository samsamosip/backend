package com.youthbenefit.notification;

import jakarta.persistence.Column;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 공고 변경 한 번. changes 는 {@link FieldChange} 목록 JSON. */
@Entity
@Table(name = "policy_changes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyChange {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long policyId;

	@Enumerated(EnumType.STRING)
	private ChangeKind kind;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private String changes;

	private LocalDateTime detectedAt;

	PolicyChange(Long policyId, ChangeKind kind, String changes) {
		this.policyId = policyId;
		this.kind = kind;
		this.changes = changes;
	}

	@PrePersist
	void onCreate() {
		detectedAt = LocalDateTime.now();
	}

}
