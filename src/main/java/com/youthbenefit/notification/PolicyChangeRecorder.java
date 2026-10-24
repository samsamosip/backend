package com.youthbenefit.notification;

import com.youthbenefit.application.ApplicationRepository;
import com.youthbenefit.policy.Policy;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** 공고 변경을 이력에 남기고, 그 공고를 준비함에 담은 사람마다 알림을 만든다. 수집기가 부른다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyChangeRecorder {

	private final PolicyChangeRepository changeRepository;

	private final NotificationRepository notificationRepository;

	private final ApplicationRepository applicationRepository;

	private final JsonMapper jsonMapper;

	/**
	 * 화면에 보이는 값이 하나도 안 바뀌었으면(조회수·수정 시각만 바뀜) 남기지 않는다. 공고 내림·다시 게시·취소는 값이 같아도 남긴다.
	 *
	 * @return 남긴 변경, 남기지 않았으면 null
	 */
	public PolicyChange record(Policy policy, ChangeKind kind, List<FieldChange> changes) {
		boolean statusOnly = kind == ChangeKind.REMOVED || kind == ChangeKind.RESTORED || kind == ChangeKind.CANCELLED;
		// 아직 저장 전인 공고(한 번의 수집에 같은 번호가 두 번 온 경우)는 남길 곳이 없다
		if (policy.getId() == null || changes.isEmpty() && !statusOnly) {
			return null;
		}
		PolicyChange change = changeRepository
			.save(new PolicyChange(policy.getId(), kind, jsonMapper.writeValueAsString(changes)));
		List<UUID> owners = applicationRepository.findOwnersByPolicyId(policy.getId());
		owners.forEach(owner -> notificationRepository.save(new Notification(owner, policy.getId(), change)));
		if (!owners.isEmpty()) {
			log.info("공고 {} {}: 알림 {}건", policy.getId(), kind.label(), owners.size());
		}
		return change;
	}

}
