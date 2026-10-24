package com.youthbenefit.harvester;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수집기가 어디까지 읽었는지 (Harvester 변경 목록의 마지막 event_seq). */
@Entity
@Table(name = "sync_cursors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncCursor {

	@Id
	private String name;

	@Column(name = "cursor")
	private String value;

	private LocalDateTime updatedAt;

	public SyncCursor(String name, String value) {
		this.name = name;
		this.value = value;
	}

	public void moveTo(String value) {
		this.value = value;
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = LocalDateTime.now();
	}

}
