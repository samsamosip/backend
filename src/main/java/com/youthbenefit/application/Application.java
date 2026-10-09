package com.youthbenefit.application;

import com.youthbenefit.policy.Policy;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 준비함의 공고 하나. 만들 때 공고 제출 서류로 체크리스트를 채운다. */
@Entity
@Table(name = "applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private UUID anonymousId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "policy_id")
	private Policy policy;

	@Enumerated(EnumType.STRING)
	private ApplicationStatus status;

	@OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC, id ASC")
	private List<ChecklistItem> items = new ArrayList<>();

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	public static Application create(UUID anonymousId, Policy policy) {
		Application application = new Application();
		application.anonymousId = anonymousId;
		application.policy = policy;
		application.status = ApplicationStatus.INTERESTED;
		for (DocumentListParser.Document doc : DocumentListParser.parse(policy.getSubmissionDocuments())) {
			application.items.add(ChecklistItem.of(application, doc.name(), doc.optional(), ChecklistItem.Source.POLICY,
					application.items.size()));
		}
		return application;
	}

	void changeStatus(ApplicationStatus status) {
		this.status = status;
	}

	ChecklistItem addItem(String content) {
		int next = items.stream().mapToInt(ChecklistItem::getPosition).max().orElse(-1) + 1;
		ChecklistItem item = ChecklistItem.of(this, content, false, ChecklistItem.Source.USER, next);
		items.add(item);
		return item;
	}

	Optional<ChecklistItem> item(Long itemId) {
		return items.stream().filter(i -> i.getId().equals(itemId)).findFirst();
	}

	void removeItem(ChecklistItem item) {
		items.remove(item);
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
