package com.youthbenefit.eligibility;

import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.PolicySource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 지금 신청 가능한 온통청년 공고 중 글 조건이 있고 아직 변환 안 됐거나 글이 바뀐 것을 AI 로 바꿔 저장한다. 공고마다 따로 저장해서 중간에 실패해도
 * 된 것은 남는다.
 */
@Slf4j
@Service
public class AiConditionService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 동시에 보내는 요청 수 */
	private static final int PARALLEL = 6;

	private final PolicyRepository policyRepository;

	private final AiConditionConverter converter;

	private final LlmClient llm;

	private final TransactionTemplate tx;

	private final JsonMapper jsonMapper;

	public AiConditionService(PolicyRepository policyRepository, AiConditionConverter converter, LlmClient llm,
			TransactionTemplate tx, JsonMapper jsonMapper) {
		this.policyRepository = policyRepository;
		this.converter = converter;
		this.llm = llm;
		this.tx = tx;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * @param pending 변환할 게 남은 공고 수(이번 실행 전)
	 * @param reviewed 2차 모델이 다시 본 수
	 * @param tokens 이번 실행에 쓴 토큰 수
	 */
	public record Result(int pending, int converted, int reviewed, int failed, long tokens) {
	}

	/** @param limit 이번에 변환할 최대 개수 (비용 조절) */
	public Result convertPending(int limit) {
		if (!llm.configured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PWC_LLM_BASE_URL, PWC_LLM_API_KEY 를 설정해 주세요");
		}
		List<Policy> pending = policyRepository.findOpen(LocalDate.now(KST), Pageable.unpaged())
			.stream()
			.filter(p -> p.getSource() == PolicySource.ONTONG && AiConditionConverter.hasText(p))
			.filter(p -> !AiConditionConverter.textHash(p).equals(p.getAiConditionsHash()))
			.toList();
		List<Policy> batch = pending.subList(0, Math.min(limit, pending.size()));
		long tokensBefore = llm.totalTokens();
		AtomicInteger converted = new AtomicInteger();
		AtomicInteger reviewed = new AtomicInteger();
		AtomicInteger failed = new AtomicInteger();
		try (ExecutorService pool = Executors.newFixedThreadPool(PARALLEL)) {
			List<Future<?>> futures = new ArrayList<>();
			for (Policy policy : batch) {
				futures.add(pool.submit(() -> {
					try {
						AiConditionConverter.Converted result = converter.convert(policy);
						save(policy.getId(), result.conditions(), AiConditionConverter.textHash(policy));
						converted.incrementAndGet();
						if (result.reviewed()) {
							reviewed.incrementAndGet();
						}
					}
					catch (RuntimeException ex) {
						failed.incrementAndGet();
						log.warn("공고 {} 조건 변환 실패: {}", policy.getId(), ex.getMessage());
					}
				}));
			}
			for (Future<?> f : futures) {
				try {
					f.get();
				}
				catch (Exception ex) {
					failed.incrementAndGet();
				}
			}
		}
		Result result = new Result(pending.size(), converted.get(), reviewed.get(), failed.get(),
				llm.totalTokens() - tokensBefore);
		log.info("글 조건 AI 변환: {}", result);
		return result;
	}

	/** 검사 규칙을 고친 뒤: 저장된 모델 답을 다시 검사만 한다 (AI 호출·비용 없음). 글이 바뀐 공고는 건너뛴다 */
	public int recheckAll() {
		List<Long> ids = tx.execute(status -> policyRepository.findAll()
			.stream()
			.filter(p -> p.getAiConditions() != null && AiConditionConverter.textHash(p).equals(p.getAiConditionsHash()))
			.map(Policy::getId)
			.toList());
		int changed = 0;
		for (Long id : ids) {
			Boolean updated = tx.execute(status -> policyRepository.findById(id).map(p -> {
				AiConditions stored = jsonMapper.readValue(p.getAiConditions(), AiConditions.class);
				if (stored.raw() == null) {
					return false;
				}
				String json = jsonMapper.writeValueAsString(converter.recheck(p, stored, jsonMapper));
				if (json.equals(p.getAiConditions())) {
					return false;
				}
				p.applyAiConditions(json, p.getAiConditionsHash());
				return true;
			}).orElse(false));
			if (Boolean.TRUE.equals(updated)) {
				changed++;
			}
		}
		log.info("글 조건 다시 검사: {}건 중 {}건 바뀜", ids.size(), changed);
		return changed;
	}

	private void save(Long policyId, AiConditions conditions, String hash) {
		String json = jsonMapper.writeValueAsString(conditions);
		tx.executeWithoutResult(status -> policyRepository.findById(policyId)
			.ifPresent(p -> p.applyAiConditions(json, hash)));
	}

}
