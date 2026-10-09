package com.youthbenefit.application;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "checklist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem {

	/** POLICY: 공고 제출 서류에서 자동으로 만든 항목, USER: 사용자가 추가한 항목 */
	public enum Source {
		POLICY, USER
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "application_id")
	private Application application;

	private String content;

	private boolean optional;

	@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
	private Source source;

	private boolean checked;

	private int position;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	static ChecklistItem of(Application application, String content, boolean optional, Source source, int position) {
		ChecklistItem item = new ChecklistItem();
		item.application = application;
		item.content = content;
		item.optional = optional;
		item.source = source;
		item.position = position;
		return item;
	}

	void check(boolean checked) {
		this.checked = checked;
	}

	void rename(String content) {
		this.content = content;
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
