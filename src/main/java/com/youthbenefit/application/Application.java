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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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

	/** 신청 완료로 표시한 시각. 다른 상태로 되돌리면 비운다 */
	private LocalDateTime appliedAt;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	public static Application create(UUID anonymousId, Policy policy) {
		Application application = new Application();
		application.anonymousId = anonymousId;
		application.policy = policy;
		application.status = ApplicationStatus.INTERESTED;
		for (DocumentListParser.Document doc : DocumentListParser.documents(policy)) {
			application.items.add(ChecklistItem.of(application, doc.name(), doc.optional(), ChecklistItem.Source.POLICY,
					application.items.size()));
		}
		return application;
	}

	/**
	 * 공고 서류 목록에 체크리스트를 맞춘다. 사용자가 추가한 항목은 건드리지 않는다.
	 * <ul>
	 * <li>새 서류: 추가 (mark 면 "새로 추가" 표시)</li>
	 * <li>빠진 서류: mark 면 지우지 않고 "공고에서 빠짐" 표시, 아니면(직접 등록 공고를 본인이 고친 경우) 지운다</li>
	 * <li>빠졌다가 다시 들어온 서류: 표시를 뗀다</li>
	 * </ul>
	 *
	 * @return 바뀐 게 있으면 true
	 */
	boolean syncDocuments(List<DocumentListParser.Document> documents, boolean mark) {
		boolean changed = false;
		Set<String> wanted = new HashSet<>();
		for (DocumentListParser.Document doc : documents) {
			String key = key(doc.name());
			if (!wanted.add(key)) {
				continue;
			}
			Optional<ChecklistItem> existing = items.stream().filter(i -> key(i.getContent()).equals(key)).findFirst();
			if (existing.isPresent()) {
				if (existing.get().getPolicyChange() == ChecklistItem.PolicyChange.DROPPED) {
					existing.get().markPolicyChange(null);
					changed = true;
				}
				continue;
			}
			ChecklistItem item = ChecklistItem.of(this, doc.name(), doc.optional(), ChecklistItem.Source.POLICY,
					nextPosition());
			if (mark) {
				item.markPolicyChange(ChecklistItem.PolicyChange.ADDED);
			}
			items.add(item);
			changed = true;
		}
		for (ChecklistItem item : List.copyOf(items)) {
			if (item.getSource() != ChecklistItem.Source.POLICY || wanted.contains(key(item.getContent()))) {
				continue;
			}
			if (!mark) {
				items.remove(item);
				changed = true;
			}
			else if (item.getPolicyChange() != ChecklistItem.PolicyChange.DROPPED) {
				item.markPolicyChange(ChecklistItem.PolicyChange.DROPPED);
				changed = true;
			}
		}
		return changed;
	}

	/** 띄어쓰기·대소문자만 다르면 같은 서류 */
	private static String key(String name) {
		return name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	private int nextPosition() {
		return items.stream().mapToInt(ChecklistItem::getPosition).max().orElse(-1) + 1;
	}

	void changeStatus(ApplicationStatus status) {
		if (status == ApplicationStatus.APPLIED && this.status != ApplicationStatus.APPLIED) {
			appliedAt = LocalDateTime.now();
		}
		else if (status != ApplicationStatus.APPLIED) {
			appliedAt = null;
		}
		this.status = status;
	}

	ChecklistItem addItem(String content) {
		ChecklistItem item = ChecklistItem.of(this, content, false, ChecklistItem.Source.USER, nextPosition());
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
