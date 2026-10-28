package com.youthbenefit.eligibility;

import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.PolicySummaryResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 지금 신청 가능한 공고와 그 판정 조건·카드 모양을 메모리에 들고 있는다 (맞춤 홈·배너·검색·목록이 같이 쓴다).
 *
 * <p>
 * 실측(10/28): 맞춤 홈이 매번 공고 1,226건을 원본(약 8MB)째 DB 에서 읽고 조건을 새로 만들어서 한 번에 0.23초, 30명이 동시에 쓰면 0.85초
 * (느린 쪽 1.6초)였다. DB 조회 자체는 0.005초라서 읽어 온 뒤의 일이 무거웠다. 이제 판정만 매번 한다.
 *
 * <p>
 * 다시 만드는 때: 공고 데이터가 바뀌었을 때(수집·AI 변환·금액 뽑기 모두 updated_at 이 바뀐다 — 요청마다 작은 조회 하나로 확인), 날짜가 바뀌었을
 * 때(마감 지난 공고 빼기).
 */
@Slf4j
@Component
public class PolicyCatalog {

	/** 공고 하나: 판정 조건과 카드 모양을 미리 만들어 둔다 */
	public record Entry(Policy policy, Condition condition, PolicySummaryResponse summary) {
	}

	private record Snapshot(LocalDate day, String version, List<Entry> entries) {
	}

	private final PolicyRepository policyRepository;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate tx;

	private volatile Snapshot snapshot;

	public PolicyCatalog(PolicyRepository policyRepository, JdbcTemplate jdbc, TransactionTemplate tx) {
		this.policyRepository = policyRepository;
		this.jdbc = jdbc;
		this.tx = tx;
	}

	/** 지금 신청 가능한 공고 (마감 임박순, 상시 모집은 뒤로). 바뀐 게 없으면 들고 있던 것 */
	public List<Entry> open(LocalDate today) {
		String version = version();
		Snapshot s = snapshot;
		if (s != null && s.day().equals(today) && s.version().equals(version)) {
			return s.entries();
		}
		synchronized (this) {
			s = snapshot;
			if (s != null && s.day().equals(today) && s.version().equals(version)) {
				return s.entries();
			}
			long started = System.currentTimeMillis();
			List<Entry> entries = tx.execute(status -> policyRepository
				.findOpen(today,
						Pageable.unpaged(Sort.by(Sort.Order.asc("applyEndDate").nullsLast(), Sort.Order.desc("id"))))
				.getContent()
				.stream()
				.map(p -> new Entry(p, EligibilityService.conditions(p), PolicySummaryResponse.from(p)))
				.toList());
			snapshot = new Snapshot(today, version, entries);
			log.info("공고 목록 다시 만듦: {}건, {}ms", entries.size(), System.currentTimeMillis() - started);
			return entries;
		}
	}

	/**
	 * 공고 데이터가 바뀌었는지 보는 값: 개수 + 모든 수정 시각의 합. 마지막 수정 시각만 보면 시각이 앞으로 가지 않는 변경(시계가 다른 곳에서 고친 경우 —
	 * 실측: DB 의 now() 는 UTC, 앱은 한국 시각)을 놓친다. 공고 4천 건 기준 1ms 안팎
	 */
	private String version() {
		return jdbc.queryForObject(
				"select count(*) || '|' || coalesce(sum(extract(epoch from updated_at)), 0) from policies", String.class);
	}

}
