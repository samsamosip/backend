package com.youthbenefit.application;

import com.youthbenefit.common.InvalidRequestException;
import com.youthbenefit.notification.NotificationRepository;
import com.youthbenefit.policy.Categories;
import com.youthbenefit.policy.CustomPolicy;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyRepository;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 공고 직접 등록 (WF07 1차: 손 입력). 등록하면 나만 보이는 공고(source = USER)가 생기고 준비함에 바로 담긴다. 판정은 없고 D-day·마감
 * 임박 알림·체크리스트는 공식 공고와 같다. 링크 불러오기·캡처 읽기는 AI 쪽이 붙으면 이 값을 채워 주는 방식으로 더한다.
 */
@Service
@RequiredArgsConstructor
public class CustomPolicyService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PolicyRepository policyRepository;

	private final ApplicationRepository applicationRepository;

	private final NotificationRepository notificationRepository;

	@Transactional
	public ApplicationResponses.Detail create(UUID owner, ApplicationRequests.Custom request) {
		Policy policy = policyRepository.save(Policy.custom(owner, input(request)));
		Application application = Application.create(owner, policy);
		if (request.status() != null) {
			application.changeStatus(request.status());
		}
		return ApplicationResponses.Detail.of(applicationRepository.save(application), today());
	}

	/** 보낸 값 전체로 바꾼다. 서류 목록이 바뀌면 체크리스트도 맞춘다(체크한 항목은 그대로, 뺀 서류는 지움, 직접 추가한 항목은 유지). */
	@Transactional
	public ApplicationResponses.Detail update(UUID owner, Long policyId, ApplicationRequests.Custom request) {
		Policy policy = owned(owner, policyId);
		policy.edit(input(request));
		Application application = applicationRepository.findByAnonymousIdAndPolicyId(owner, policyId)
			.orElseGet(() -> applicationRepository.save(Application.create(owner, policy)));
		application.syncDocuments(DocumentListParser.documents(policy), false);
		applicationRepository.flush();
		return ApplicationResponses.Detail.of(application, today());
	}

	/** 직접 등록 공고와 그 준비함·알림을 지운다. */
	@Transactional
	public void delete(UUID owner, Long policyId) {
		delete(owned(owner, policyId));
	}

	/** 준비함에서 빼면 직접 등록 공고는 남을 곳이 없어서 같이 지운다({@link ApplicationService#delete}). */
	@Transactional
	void delete(Policy policy) {
		notificationRepository.deleteByPolicyId(policy.getId());
		applicationRepository.findByAnonymousIdAndPolicyId(policy.getOwnerId(), policy.getId())
			.ifPresent(applicationRepository::delete);
		applicationRepository.flush();
		policyRepository.delete(policy);
	}

	private Policy owned(UUID owner, Long policyId) {
		return policyRepository.findById(policyId)
			.filter(p -> p.isCustom() && p.getOwnerId().equals(owner))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "직접 등록한 공고가 아닙니다: " + policyId));
	}

	private static CustomPolicy input(ApplicationRequests.Custom r) {
		Map<String, String> errors = new LinkedHashMap<>();
		String category = blankToNull(r.category());
		if (category != null && !Categories.OFFICIAL.contains(category)) {
			errors.put("category", String.join(", ", Categories.OFFICIAL) + " 중 하나여야 합니다");
		}
		String url = blankToNull(r.applyUrl());
		if (url != null && !isWebLink(url)) {
			errors.put("applyUrl", "http:// 나 https:// 로 시작하는 주소를 넣어 주세요");
		}
		if (r.applyEndTime() != null && r.applyEndDate() == null) {
			errors.put("applyEndTime", "마감 시각은 마감일과 함께 넣어 주세요");
		}
		if (!errors.isEmpty()) {
			throw new InvalidRequestException(errors);
		}
		List<String> documents = r.documents() == null ? List.of()
				: r.documents().stream().map(String::strip).distinct().toList();
		return new CustomPolicy(r.title().strip(), blankToNull(r.organization()), r.applyEndDate(), r.applyEndTime(),
				category, blankToNull(r.memo()), url, documents);
	}

	private static boolean isWebLink(String url) {
		try {
			URI uri = URI.create(url);
			return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					&& uri.getHost() != null;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

	private static LocalDate today() {
		return LocalDate.now(KST);
	}

}
