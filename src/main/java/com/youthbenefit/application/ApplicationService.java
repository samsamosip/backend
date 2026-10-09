package com.youthbenefit.application;

import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ApplicationService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ApplicationRepository repository;

	private final PolicyRepository policyRepository;

	/** 관심 저장. 이미 있으면 그것을 돌려준다(두 번 눌러도 하나). created 는 새로 만들었는지. */
	@Transactional
	public Created create(UUID owner, Long policyId) {
		return repository.findByAnonymousIdAndPolicyId(owner, policyId)
			.map(existing -> new Created(ApplicationResponses.Detail.of(existing, today()), false))
			.orElseGet(() -> {
				Policy policy = policyRepository.findById(policyId)
					.orElseThrow(() -> notFound("공고를 찾을 수 없습니다: " + policyId));
				Application saved = repository.save(Application.create(owner, policy));
				return new Created(ApplicationResponses.Detail.of(saved, today()), true);
			});
	}

	public record Created(ApplicationResponses.Detail application, boolean created) {
	}

	@Transactional(readOnly = true)
	public List<ApplicationResponses.Summary> list(UUID owner) {
		LocalDate today = today();
		return repository.findAllByAnonymousIdOrderByIdDesc(owner)
			.stream()
			.map(a -> ApplicationResponses.Summary.of(a, today))
			.toList();
	}

	@Transactional(readOnly = true)
	public ApplicationResponses.Detail get(UUID owner, Long id) {
		return ApplicationResponses.Detail.of(find(owner, id), today());
	}

	@Transactional
	public ApplicationResponses.Detail changeStatus(UUID owner, Long id, ApplicationStatus status) {
		Application application = find(owner, id);
		application.changeStatus(status);
		return ApplicationResponses.Detail.of(application, today());
	}

	/** 관심 해제. 체크리스트도 함께 지운다. */
	@Transactional
	public void delete(UUID owner, Long id) {
		repository.delete(find(owner, id));
	}

	@Transactional
	public ApplicationResponses.Detail addItem(UUID owner, Long id, String content) {
		Application application = find(owner, id);
		application.addItem(content.strip());
		repository.flush();
		return ApplicationResponses.Detail.of(application, today());
	}

	@Transactional
	public ApplicationResponses.Detail updateItem(UUID owner, Long id, Long itemId, ApplicationRequests.UpdateItem req) {
		Application application = find(owner, id);
		ChecklistItem item = application.item(itemId).orElseThrow(() -> notFound("항목을 찾을 수 없습니다: " + itemId));
		if (req.checked() != null) {
			item.check(req.checked());
		}
		if (req.content() != null && !req.content().isBlank()) {
			item.rename(req.content().strip());
		}
		return ApplicationResponses.Detail.of(application, today());
	}

	@Transactional
	public ApplicationResponses.Detail deleteItem(UUID owner, Long id, Long itemId) {
		Application application = find(owner, id);
		ChecklistItem item = application.item(itemId).orElseThrow(() -> notFound("항목을 찾을 수 없습니다: " + itemId));
		application.removeItem(item);
		return ApplicationResponses.Detail.of(application, today());
	}

	/** 다른 사람의 준비함은 없는 것과 같다(404). */
	private Application find(UUID owner, Long id) {
		return repository.findByIdAndAnonymousId(id, owner).orElseThrow(() -> notFound("준비함에 없는 항목입니다: " + id));
	}

	private static ResponseStatusException notFound(String message) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
	}

	private static LocalDate today() {
		return LocalDate.now(KST);
	}

}
