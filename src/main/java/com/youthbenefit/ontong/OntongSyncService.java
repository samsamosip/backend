package com.youthbenefit.ontong;

import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyContent;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.PolicySource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온통청년 정책 전체를 받아 저장한다. 같은 plcyNo 가 있으면 원본 해시가 바뀐 경우에만 갱신한다.
 * 마감(0057003) 공고도 저장해 두고 조회할 때 거른다 — 이미 저장된 공고가 마감으로 바뀐 것을 반영하기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OntongSyncService {

	private final OntongApiClient apiClient;

	private final OntongPolicyMapper mapper;

	private final PolicyRepository policyRepository;

	@Transactional
	public OntongSyncResult sync() {
		List<Map<String, Object>> items = apiClient.fetchAll();
		Map<String, Policy> existing = policyRepository.findAllBySource(PolicySource.ONTONG)
			.stream()
			.collect(Collectors.toMap(Policy::getSourceId, Function.identity()));

		List<Policy> created = new ArrayList<>();
		int updated = 0;
		int unchanged = 0;
		for (Map<String, Object> item : items) {
			String sourceId = mapper.sourceId(item);
			if (sourceId == null) {
				continue;
			}
			PolicyContent content = mapper.toContent(item);
			Policy policy = existing.get(sourceId);
			if (policy == null) {
				Policy newPolicy = Policy.create(PolicySource.ONTONG, sourceId, content);
				created.add(newPolicy);
				existing.put(sourceId, newPolicy);
			}
			else if (policy.apply(content)) {
				updated++;
			}
			else {
				unchanged++;
			}
		}
		policyRepository.saveAll(created);

		OntongSyncResult result = new OntongSyncResult(items.size(), created.size(), updated, unchanged);
		log.info("온통청년 수집 완료: {}", result);
		return result;
	}

}
