package com.youthbenefit.policy;

import com.youthbenefit.application.DocumentGuides;
import com.youthbenefit.application.DocumentListParser;
import com.youthbenefit.auth.CurrentOwner;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "공고", description = "WF02 맞춤 홈, WF03 공고 상세")
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PolicyRepository policyRepository;

	/**
	 * 지금 신청할 수 있는 공고 목록. 마감 임박순, 마감일 없는(상시) 공고는 뒤로. category 는 공식 대분류 5개 중 하나
	 * (일자리, 주거, 교육, 복지문화, 참여권리).
	 */
	@GetMapping
	public PageResponse<PolicySummaryResponse> list(@RequestParam(required = false) String category,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		LocalDate today = LocalDate.now(KST);
		PageRequest pageable = PageRequest.of(page, Math.min(size, 100),
				Sort.by(Sort.Order.asc("applyEndDate").nullsLast(), Sort.Order.desc("id")));
		Page<Policy> result = (category == null || category.isBlank())
				? policyRepository.findOpen(today, pageable)
				: policyRepository.findOpenByCategory(today, category, pageable);
		return PageResponse.of(result, PolicySummaryResponse::from);
	}

	/** 직접 등록 공고는 등록한 사람만 볼 수 있다(다른 사람에게는 404). applyEndTime·memo 는 직접 등록 공고만 */
	@GetMapping("/{id}")
	public PolicyResponse get(@PathVariable Long id, @CurrentOwner(required = false) UUID viewer) {
		return policyRepository.findById(id)
			.filter(p -> p.visibleTo(viewer))
			.map(p -> PolicyResponse.from(p, DocumentListParser.documents(p)
				.stream()
				.map(d -> new PolicyResponse.Document(d.name(), d.optional(), DocumentGuides.of(d.name())))
				.toList()))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공고를 찾을 수 없습니다: " + id));
	}

}
