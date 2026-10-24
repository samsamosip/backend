package com.youthbenefit.notification;

import com.youthbenefit.notification.NotificationResponses.Detail;
import com.youthbenefit.notification.NotificationResponses.History;
import com.youthbenefit.notification.NotificationResponses.Summary;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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

	private final NotificationRepository notificationRepository;

	private final PolicyChangeRepository changeRepository;

	private final PolicyRepository policyRepository;

	private final JsonMapper jsonMapper;

	@Transactional(readOnly = true)
	public NotificationResponses.Page list(UUID owner, int page, int size) {
		var result = notificationRepository.findAllByAnonymousIdOrderByIdDesc(owner, PageRequest.of(page, size));
		Map<Long, String> titles = titles(result.getContent().stream().map(Notification::getPolicyId).toList());
		List<Summary> items = result.getContent().stream().map(n -> {
			PolicyChange c = n.getPolicyChange();
			return new Summary(n.getId(), n.getPolicyId(), titles.get(n.getPolicyId()), c.getKind(),
					c.getKind().label(), summary(c), n.getReadAt() != null, n.getCreatedAt());
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
		return new Detail(n.getId(), n.getPolicyId(), titles(List.of(n.getPolicyId())).get(n.getPolicyId()),
				c.getKind(), c.getKind().label(), changes(c), true, n.getCreatedAt());
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

	/** "마감일 2026-10-30 → 2026-11-15 외 2건". 바뀐 값이 없으면(공고 내림 등) 종류 이름. */
	String summary(PolicyChange c) {
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

	private Map<Long, String> titles(List<Long> policyIds) {
		return policyRepository.findAllById(policyIds)
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
