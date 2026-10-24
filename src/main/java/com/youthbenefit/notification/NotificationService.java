package com.youthbenefit.notification;

import com.youthbenefit.common.InvalidRequestException;
import com.youthbenefit.eligibility.Verdict;
import com.youthbenefit.notification.NotificationResponses.Deadline;
import com.youthbenefit.notification.NotificationResponses.Detail;
import com.youthbenefit.notification.NotificationResponses.History;
import com.youthbenefit.notification.NotificationResponses.NewMatches;
import com.youthbenefit.notification.NotificationResponses.SettingsBody;
import com.youthbenefit.notification.NotificationResponses.Summary;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M/d");

	private final NotificationRepository notificationRepository;

	private final PolicyChangeRepository changeRepository;

	private final NotificationSettingsRepository settingsRepository;

	private final PolicyRepository policyRepository;

	private final JsonMapper jsonMapper;

	/** type 을 주면 그 종류만(알림함 탭). */
	@Transactional(readOnly = true)
	public NotificationResponses.Page list(UUID owner, NotificationType type, int page, int size) {
		var pageable = PageRequest.of(page, size);
		var result = type == null ? notificationRepository.findAllByAnonymousIdOrderByIdDesc(owner, pageable)
				: notificationRepository.findAllByAnonymousIdAndTypeOrderByIdDesc(owner, type, pageable);
		Map<Long, String> titles = titles(result.getContent().stream().map(Notification::getPolicyId).toList());
		List<Summary> items = result.getContent().stream().map(n -> {
			ChangeKind kind = n.getPolicyChange() == null ? null : n.getPolicyChange().getKind();
			return new Summary(n.getId(), n.getType(), label(n), kind, n.getPolicyId(), titles.get(n.getPolicyId()),
					summary(n), n.getReadAt() != null, n.getCreatedAt());
		}).toList();
		return new NotificationResponses.Page(notificationRepository.countByAnonymousIdAndReadAtIsNull(owner), items,
				page, size, result.getTotalElements(), result.getTotalPages());
	}

	/** 알림을 열면 읽음으로 표시한다. */
	@Transactional
	public Detail open(UUID owner, Long id) {
		Notification n = notificationRepository.findByIdAndAnonymousId(id, owner)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다: " + id));
		n.markRead();
		PolicyChange c = n.getPolicyChange();
		String title = n.getPolicyId() == null ? null : titles(List.of(n.getPolicyId())).get(n.getPolicyId());
		return new Detail(n.getId(), n.getType(), label(n), c == null ? null : c.getKind(), n.getPolicyId(), title,
				summary(n), c == null ? List.of() : changes(c),
				n.getType() == NotificationType.NEW_MATCHES ? payload(n, NewMatches.class) : null,
				n.getType() == NotificationType.DEADLINE ? payload(n, Deadline.class) : null, true, n.getCreatedAt());
	}

	@Transactional
	public int readAll(UUID owner) {
		return notificationRepository.markAllRead(owner, LocalDateTime.now());
	}

	@Transactional
	public void delete(UUID owner, Long id) {
		Notification n = notificationRepository.findByIdAndAnonymousId(id, owner)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다: " + id));
		notificationRepository.delete(n);
	}

	@Transactional(readOnly = true)
	public List<History> history(Long policyId) {
		if (!policyRepository.existsById(policyId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다: " + policyId);
		}
		return changeRepository.findAllByPolicyIdOrderByIdDesc(policyId)
			.stream()
			.map(c -> new History(c.getId(), c.getKind(), c.getKind().label(), changes(c), c.getDetectedAt()))
			.toList();
	}

	@Transactional(readOnly = true)
	public SettingsBody settings(UUID owner) {
		return body(settingsRepository.findById(owner).orElseGet(() -> NotificationSettings.defaults(owner)));
	}

	@Transactional
	public SettingsBody saveSettings(UUID owner, SettingsBody body) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (body.newMatchVerdicts() == null || body.newMatchVerdicts().contains(Verdict.NO_MATCH)) {
			errors.put("newMatchVerdicts", "MATCH, NEEDS_CHECK 중에서 골라 주세요");
		}
		if (body.deadlineDays() == null || body.deadlineDays().stream().anyMatch(d -> d == null || d < 0 || d > 30)) {
			errors.put("deadlineDays", "0~30 사이 숫자 목록이어야 합니다 (0 = 당일)");
		}
		if ((body.quietStart() == null) != (body.quietEnd() == null)) {
			errors.put("quietStart", "방해금지 시작과 끝을 함께 보내 주세요");
		}
		if (!errors.isEmpty()) {
			throw new InvalidRequestException(errors);
		}
		NotificationSettings settings = settingsRepository.findById(owner)
			.orElseGet(() -> NotificationSettings.defaults(owner));
		settings.replace(body);
		return body(settingsRepository.save(settings));
	}

	private static SettingsBody body(NotificationSettings s) {
		return new SettingsBody(s.isNewMatches(), s.verdicts(), s.isDeadline(), s.days(), s.isChanges(),
				s.getQuietStart(), s.getQuietEnd());
	}

	private static String label(Notification n) {
		return n.getType() == NotificationType.CHANGE && n.getPolicyChange() != null
				? n.getPolicyChange().getKind().label() : n.getType().label();
	}

	/** 한 줄 요약: 변경 "마감일 10/30 → 11/15 외 1건", 새 공고 "내 조건에 맞는 새 공고 3건", 마감 "마감 3일 전이에요 (11/14 마감)". */
	String summary(Notification n) {
		return switch (n.getType()) {
			case CHANGE -> changeSummary(n.getPolicyChange());
			case NEW_MATCHES -> "내 조건에 맞는 새 공고 " + payload(n, NewMatches.class).total() + "건";
			case DEADLINE -> {
				Deadline d = payload(n, Deadline.class);
				yield d.daysLeft() == 0 ? "오늘 마감이에요" : "마감 " + d.daysLeft() + "일 전이에요 ("
						+ d.applyEndDate().format(MONTH_DAY) + " 마감)";
			}
		};
	}

	private String changeSummary(PolicyChange c) {
		List<FieldChange> changes = changes(c);
		if (changes.isEmpty()) {
			return c.getKind() == ChangeKind.REMOVED ? "공고가 내려갔어요" : c.getKind().label();
		}
		FieldChange first = changes.getFirst();
		String line = first.label() + " " + shorten(first.before()) + " → " + shorten(first.after());
		return changes.size() == 1 ? line : line + " 외 " + (changes.size() - 1) + "건";
	}

	private List<FieldChange> changes(PolicyChange c) {
		return List.of(jsonMapper.readValue(c.getChanges(), FieldChange[].class));
	}

	private <T> T payload(Notification n, Class<T> type) {
		return jsonMapper.readValue(n.getPayload(), type);
	}

	private Map<Long, String> titles(List<Long> policyIds) {
		List<Long> ids = policyIds.stream().filter(Objects::nonNull).toList();
		return policyRepository.findAllById(ids)
			.stream()
			.collect(Collectors.toMap(Policy::getId, Policy::getTitle, (a, b) -> a));
	}

	/** 긴 글(지원 내용 등)은 요약에서 앞부분만 */
	private static String shorten(String value) {
		if (value == null) {
			return "없음";
		}
		String oneLine = value.replaceAll("\\s+", " ").strip();
		return oneLine.length() <= 20 ? oneLine : oneLine.substring(0, 19) + "…";
	}

}
