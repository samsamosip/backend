package com.youthbenefit.notification;

import com.youthbenefit.application.Application;
import com.youthbenefit.application.ApplicationRepository;
import com.youthbenefit.application.ApplicationStatus;
import com.youthbenefit.eligibility.EligibilityService;
import com.youthbenefit.eligibility.PolicyEligibility;
import com.youthbenefit.eligibility.ProfileFacts;
import com.youthbenefit.eligibility.Verdict;
import com.youthbenefit.harvester.SyncCursor;
import com.youthbenefit.harvester.SyncCursorRepository;
import com.youthbenefit.notification.NotificationResponses.Deadline;
import com.youthbenefit.notification.NotificationResponses.NewMatches;
import com.youthbenefit.notification.NotificationResponses.NewPolicy;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.profile.UserProfile;
import com.youthbenefit.profile.UserProfileRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 매일 한 번 만드는 알림 (WF05).
 * <ul>
 * <li>새 맞춤 공고: 지난번 이후 새로 저장된 신청 가능 공고를 프로필이 있는 사람마다 판정해서, 설정한 판정(기본 조건 일치·확인 필요)에
 * 해당하는 것을 하루 1회 한 알림으로 묶는다. 처음 실행하면 기준 시점만 잡는다 — 기존 공고 수천 건이 "새 공고"로 쏟아지지 않게.</li>
 * <li>마감 임박: 준비함의 관심·준비 중 공고(신청 완료 제외)가 마감 7·3·1·0일 전(설정)이면 알린다.</li>
 * </ul>
 * 같은 날 두 번 돌려도 알림이 늘지 않는다(dedupe_key).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyNotificationService {

	static final String NEW_MATCHES_CURSOR = "new-matches";

	/** 새 공고 알림 하나에 담는 공고 수 */
	static final int MAX_POLICIES = 20;

	private final PolicyRepository policyRepository;

	private final UserProfileRepository profileRepository;

	private final ApplicationRepository applicationRepository;

	private final NotificationRepository notificationRepository;

	private final NotificationSettingsRepository settingsRepository;

	private final SyncCursorRepository cursorRepository;

	private final EligibilityService eligibilityService;

	private final JsonMapper jsonMapper;

	public record Result(int newMatchNotifications, int newPolicies, int deadlineNotifications) {
	}

	/**
	 * @param today 한국 날짜 (마감 며칠 전 계산)
	 * @param now 서버 시각 — 공고 저장 시각(created_at)과 같은 시계여야 새 공고를 빠뜨리지 않는다
	 */
	@Transactional
	public Result run(LocalDate today, LocalDateTime now) {
		int deadlines = deadlines(today);
		int[] newMatches = newMatches(today, now);
		Result result = new Result(newMatches[0], newMatches[1], deadlines);
		log.info("매일 알림 완료: {}", result);
		return result;
	}

	private int[] newMatches(LocalDate today, LocalDateTime now) {
		SyncCursor cursor = cursorRepository.findById(NEW_MATCHES_CURSOR).orElse(null);
		if (cursor == null) {
			cursorRepository.save(new SyncCursor(NEW_MATCHES_CURSOR, now.toString()));
			return new int[] { 0, 0 };
		}
		List<Policy> fresh = policyRepository.findOpenCreatedAfter(today, LocalDateTime.parse(cursor.getValue()));
		cursor.moveTo(now.toString());
		if (fresh.isEmpty()) {
			return new int[] { 0, 0 };
		}
		Map<UUID, NotificationSettings> settings = settingsRepository.findAll()
			.stream()
			.collect(Collectors.toMap(NotificationSettings::getAnonymousId, Function.identity()));
		int created = 0;
		for (UserProfile profile : profileRepository.findAll()) {
			UUID owner = profile.getAnonymousId();
			NotificationSettings s = settings.getOrDefault(owner, NotificationSettings.defaults(owner));
			String key = "new-matches:" + today;
			if (!s.isNewMatches() || notificationRepository.existsByAnonymousIdAndDedupeKey(owner, key)) {
				continue;
			}
			ProfileFacts facts = ProfileFacts.of(profile, today);
			List<NewPolicy> matched = fresh.stream().map(p -> {
				PolicyEligibility e = eligibilityService.judge(p, facts);
				return new NewPolicy(p.getId(), p.getTitle(), p.getOrganization(), e.verdict().name(), e.verdictLabel());
			}).filter(n -> s.verdicts().contains(Verdict.valueOf(n.verdict()))).toList();
			if (matched.isEmpty()) {
				continue;
			}
			// 조건 일치를 앞에
			List<NewPolicy> ordered = matched.stream()
				.sorted((a, b) -> Boolean.compare(!"MATCH".equals(a.verdict()), !"MATCH".equals(b.verdict())))
				.limit(MAX_POLICIES)
				.toList();
			String payload = jsonMapper.writeValueAsString(new NewMatches(matched.size(), ordered));
			notificationRepository.save(Notification.of(owner, NotificationType.NEW_MATCHES, null, payload, key));
			created++;
		}
		return new int[] { created, fresh.size() };
	}

	private int deadlines(LocalDate today) {
		int created = 0;
		Map<UUID, NotificationSettings> settings = settingsRepository.findAll()
			.stream()
			.collect(Collectors.toMap(NotificationSettings::getAnonymousId, Function.identity()));
		for (Application a : applicationRepository.findAllWaitingWithDeadline(today, today.plusDays(30))) {
			if (a.getStatus() == ApplicationStatus.APPLIED) {
				continue;
			}
			UUID owner = a.getAnonymousId();
			NotificationSettings s = settings.getOrDefault(owner, NotificationSettings.defaults(owner));
			Policy p = a.getPolicy();
			int daysLeft = (int) ChronoUnit.DAYS.between(today, p.getApplyEndDate());
			String key = "deadline:" + p.getId() + ":" + daysLeft;
			if (!s.isDeadline() || !s.days().contains(daysLeft)
					|| notificationRepository.existsByAnonymousIdAndDedupeKey(owner, key)) {
				continue;
			}
			String payload = jsonMapper.writeValueAsString(new Deadline(p.getApplyEndDate(), daysLeft));
			notificationRepository.save(Notification.of(owner, NotificationType.DEADLINE, p.getId(), payload, key));
			created++;
		}
		return created;
	}

}
