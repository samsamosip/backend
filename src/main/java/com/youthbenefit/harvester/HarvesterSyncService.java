package com.youthbenefit.harvester;

import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyContent;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.PolicySource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 학교 장학 공고를 policy-harvester 에서 받아 source = SCHOOL 로 저장한다.
 * 목록은 요약만 주고 자격·기간·서류는 상세에 있는데, 상세 요청은 분당 한도가 있어서 필요한 것만 받는다:
 * 처음 보는 공고, 버전(version_id)이 바뀐 공고, 아직 마감 안 된 공고(상태가 날짜에 따라 바뀜).
 * 처음 보는 공고 중 지난 학년도 이전 것은 이미 끝난 장학이라 받지 않는다.
 * 상세 하나가 실패해도 나머지는 계속 받는다. 요청마다 따로 저장해서 긴 트랜잭션을 잡지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HarvesterSyncService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final HarvesterApiClient apiClient;

	private final HarvesterPolicyMapper mapper;

	private final PolicyRepository policyRepository;

	public HarvesterSyncResult sync() {
		LocalDate today = LocalDate.now(KST);
		List<Map<String, Object>> items = apiClient.listAll();
		Map<String, Policy> existing = policyRepository.findAllBySource(PolicySource.SCHOOL)
			.stream()
			.collect(Collectors.toMap(Policy::getSourceId, Function.identity()));

		int skippedOld = 0;
		int fetched = 0;
		int created = 0;
		int updated = 0;
		int unchanged = 0;
		int failed = 0;
		for (Map<String, Object> item : items) {
			String id = Objects.toString(item.get("opportunity_id"), null);
			if (id == null) {
				continue;
			}
			Policy policy = existing.get(id);
			if (policy == null && isOldAcademicYear(item.get("academic_year"), today)) {
				skippedOld++;
				continue;
			}
			if (policy != null && Objects.equals(policy.getSourceVersion(), item.get("version_id"))
					&& isClosed(policy, today)) {
				unchanged++;
				continue;
			}
			PolicyContent content;
			try {
				content = mapper.toContent(item, apiClient.detail(id));
				fetched++;
			}
			catch (HarvesterApiException ex) {
				log.warn("학교 장학 상세 받기 실패: {}", ex.getMessage());
				failed++;
				continue;
			}
			if (policy == null) {
				existing.put(id, policyRepository.save(Policy.create(PolicySource.SCHOOL, id, content)));
				created++;
			}
			else {
				if (policy.apply(content)) {
					updated++;
				}
				else {
					unchanged++;
				}
				policyRepository.save(policy);
			}
		}
		HarvesterSyncResult result = new HarvesterSyncResult(items.size(), skippedOld, fetched, created, updated,
				unchanged, failed);
		log.info("학교 장학 수집 완료: {}", result);
		return result;
	}

	/** 지난 학년도보다 이전(올해가 2026 이면 2024 이하) */
	static boolean isOldAcademicYear(Object academicYear, LocalDate today) {
		return academicYear instanceof Number year && year.intValue() < today.getYear() - 1;
	}

	private static boolean isClosed(Policy p, LocalDate today) {
		return Policy.APPLY_PERIOD_CLOSED.equals(p.getApplyPeriodCode())
				|| p.getApplyEndDate() != null && p.getApplyEndDate().isBefore(today);
	}

}
