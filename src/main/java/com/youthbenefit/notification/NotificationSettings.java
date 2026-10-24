package com.youthbenefit.notification;

import com.youthbenefit.eligibility.Verdict;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알림 수신 설정 (WF05-b). 저장한 적이 없으면 {@link #defaults}(전부 켜짐). */
@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSettings {

	static final List<Integer> DEFAULT_DEADLINE_DAYS = List.of(7, 3, 1, 0);

	@Id
	private UUID anonymousId;

	private boolean newMatches;

	/** 콤마 목록: MATCH, NEEDS_CHECK */
	private String newMatchVerdicts;

	private boolean deadline;

	/** 콤마 목록, 0 = 당일 */
	private String deadlineDays;

	private boolean changes;

	private LocalTime quietStart;

	private LocalTime quietEnd;

	private LocalDateTime updatedAt;

	static NotificationSettings defaults(UUID owner) {
		NotificationSettings s = new NotificationSettings();
		s.anonymousId = owner;
		s.newMatches = true;
		s.newMatchVerdicts = "MATCH,NEEDS_CHECK";
		s.deadline = true;
		s.deadlineDays = join(DEFAULT_DEADLINE_DAYS);
		s.changes = true;
		return s;
	}

	void replace(NotificationResponses.SettingsBody body) {
		newMatches = body.newMatches();
		newMatchVerdicts = body.newMatchVerdicts().stream().map(Enum::name).distinct().collect(Collectors.joining(","));
		deadline = body.deadline();
		deadlineDays = join(body.deadlineDays().stream().distinct().sorted((a, b) -> b - a).toList());
		changes = body.changes();
		quietStart = body.quietStart();
		quietEnd = body.quietEnd();
	}

	public List<Verdict> verdicts() {
		return newMatchVerdicts.isBlank() ? List.of()
				: Arrays.stream(newMatchVerdicts.split(",")).map(Verdict::valueOf).toList();
	}

	public List<Integer> days() {
		return deadlineDays.isBlank() ? List.of() : Arrays.stream(deadlineDays.split(",")).map(Integer::valueOf).toList();
	}

	private static String join(List<Integer> days) {
		return days.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = LocalDateTime.now();
	}

}
