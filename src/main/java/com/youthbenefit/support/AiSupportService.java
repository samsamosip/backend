package com.youthbenefit.support;

import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.PolicySource;
import com.youthbenefit.policy.Support;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/** 지금 신청 가능한 온통청년 공고 중 지원 금액을 아직 안 뽑았거나 글이 바뀐 것을 뽑아 저장한다. 학교 장학은 Harvester 금액을 쓴다. */
@Slf4j
@Service
public class AiSupportService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private static final int PARALLEL = 6;

	private final PolicyRepository policyRepository;

	private final AiSupportExtractor extractor;

	private final LlmClient llm;

	private final TransactionTemplate tx;

	private final JsonMapper jsonMapper;

	public AiSupportService(PolicyRepository policyRepository, AiSupportExtractor extractor, LlmClient llm,
			TransactionTemplate tx, JsonMapper jsonMapper) {
		this.policyRepository = policyRepository;
		this.extractor = extractor;
		this.llm = llm;
		this.tx = tx;
		this.jsonMapper = jsonMapper;
	}

	/** @param withAmount 이번에 금액까지 뽑힌 수 */
	public record Result(int pending, int extracted, int withAmount, int failed, long tokens) {
	}

	public Result extractPending(int limit) {
		if (!llm.configured()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PWC_LLM_BASE_URL, PWC_LLM_API_KEY 를 설정해 주세요");
		}
		List<Policy> pending = policyRepository.findOpen(LocalDate.now(KST), Pageable.unpaged())
			.stream()
			.filter(p -> p.getSource() == PolicySource.ONTONG && p.getSupportContent() != null)
			.filter(p -> !Support.hash(p).equals(p.getAiSupportHash()))
			.toList();
		List<Policy> batch = pending.subList(0, Math.min(limit, pending.size()));
		long tokensBefore = llm.totalTokens();
		AtomicInteger extracted = new AtomicInteger();
		AtomicInteger withAmount = new AtomicInteger();
		AtomicInteger failed = new AtomicInteger();
		try (ExecutorService pool = Executors.newFixedThreadPool(PARALLEL)) {
			List<Future<?>> futures = new ArrayList<>();
			for (Policy policy : batch) {
				futures.add(pool.submit(() -> {
					try {
						Map<String, Object> result = extractor.extract(policy);
						save(policy.getId(), jsonMapper.writeValueAsString(result), Support.hash(policy));
						extracted.incrementAndGet();
						if (result.get("amountWon") != null) {
							withAmount.incrementAndGet();
						}
					}
					catch (RuntimeException ex) {
						failed.incrementAndGet();
						log.warn("공고 {} 지원 금액 뽑기 실패: {}", policy.getId(), ex.getMessage());
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
		Result result = new Result(pending.size(), extracted.get(), withAmount.get(), failed.get(),
				llm.totalTokens() - tokensBefore);
		log.info("지원 금액 뽑기: {}", result);
		return result;
	}

	/** 검사 규칙을 고친 뒤: 저장된 AI 답을 다시 검사만 한다 (AI 호출 없음) */
	public int recheckAll() {
		List<Long> ids = tx.execute(status -> policyRepository.findAll()
			.stream()
			.filter(p -> p.getAiSupport() != null && Support.hash(p).equals(p.getAiSupportHash()))
			.map(Policy::getId)
			.toList());
		int changed = 0;
		for (Long id : ids) {
			Boolean updated = tx.execute(status -> policyRepository.findById(id).map(p -> {
				var stored = jsonMapper.readTree(p.getAiSupport());
				String model = stored.path("model").asString("");
				var raw = jsonMapper.readTree(stored.path("raw").asString("{}"));
				String json = jsonMapper.writeValueAsString(AiSupportExtractor.check(raw, AiSupportExtractor.sourceText(p), model));
				if (json.equals(p.getAiSupport())) {
					return false;
				}
				p.applyAiSupport(json, p.getAiSupportHash());
				return true;
			}).orElse(false));
			if (Boolean.TRUE.equals(updated)) {
				changed++;
			}
		}
		return changed;
	}

	private void save(Long policyId, String json, String hash) {
		tx.executeWithoutResult(status -> policyRepository.findById(policyId).ifPresent(p -> p.applyAiSupport(json, hash)));
	}

}
