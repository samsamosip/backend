package com.youthbenefit.profile;

import com.youthbenefit.application.ApplicationRepository;
import com.youthbenefit.code.OntongCodes;
import com.youthbenefit.notification.NotificationRepository;
import com.youthbenefit.notification.NotificationSettingsRepository;
import com.youthbenefit.common.InvalidRequestException;
import com.youthbenefit.policy.Categories;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.Regions;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class ProfileService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** 학교에서 쓰는 평점 만점 */
	static final Set<java.math.BigDecimal> GPA_SCALES = Stream.of("4", "4.3", "4.5", "100")
		.map(v -> new java.math.BigDecimal(v).stripTrailingZeros())
		.collect(Collectors.toUnmodifiableSet());

	/** PATCH 로 받을 수 있는 항목 이름 = ProfileRequest 의 항목들 */
	static final Set<String> FIELDS = Stream.of(ProfileRequest.class.getRecordComponents())
		.map(java.lang.reflect.RecordComponent::getName)
		.collect(Collectors.toUnmodifiableSet());

	private final UserProfileRepository repository;

	private final ApplicationRepository applicationRepository;

	private final NotificationRepository notificationRepository;

	private final NotificationSettingsRepository notificationSettingsRepository;

	private final PolicyRepository policyRepository;

	private final JsonMapper jsonMapper;

	private final Validator validator;

	@Transactional(readOnly = true)
	public Optional<ProfileResponse> find(UUID anonymousId) {
		return repository.findByAnonymousId(anonymousId).map(p -> ProfileResponse.from(p, today()));
	}

	@Transactional
	public ProfileResponse save(UUID anonymousId, ProfileRequest request) {
		validateCodes(request);
		UserProfile profile = repository.findByAnonymousId(anonymousId)
			.orElseGet(() -> repository.save(UserProfile.create(anonymousId)));
		profile.replace(request);
		repository.flush();
		return ProfileResponse.from(profile, today());
	}

	/**
	 * 보낸 항목만 바꾼다(PATCH). 보내지 않은 항목은 그대로, null 을 보낸 항목은 "모름"으로 비운다. 프로필이 없으면 새로 만든다.
	 * 지금 값에 보낸 값을 덮어쓴 결과를 PUT 과 같은 규칙으로 검사한다.
	 */
	@Transactional
	public ProfileResponse patch(UUID anonymousId, ObjectNode changes) {
		Map<String, String> unknown = new LinkedHashMap<>();
		changes.propertyNames().stream().filter(f -> !FIELDS.contains(f)).forEach(f -> unknown.put(f, "알 수 없는 항목입니다"));
		if (!unknown.isEmpty()) {
			throw new InvalidRequestException(unknown);
		}
		UserProfile profile = repository.findByAnonymousId(anonymousId)
			.orElseGet(() -> repository.save(UserProfile.create(anonymousId)));
		ObjectNode merged = jsonMapper.valueToTree(profile.toRequest());
		merged.setAll(changes);
		ProfileRequest request = jsonMapper.treeToValue(merged, ProfileRequest.class);
		validate(request);
		validateCodes(request);
		profile.replace(request);
		repository.flush();
		return ProfileResponse.from(profile, today());
	}

	/** @Valid 와 같은 검사(형식·범위). PATCH 는 합친 뒤에 검사해야 해서 직접 부른다. */
	private void validate(ProfileRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		validator.validate(request).forEach(v -> errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
		if (!errors.isEmpty()) {
			throw new InvalidRequestException(errors);
		}
	}

	/** 내 데이터 삭제: 프로필, 준비함, 직접 등록 공고, 알림 전부. 지울 게 하나도 없으면 false. */
	@Transactional
	public boolean delete(UUID anonymousId) {
		long applications = applicationRepository.deleteByAnonymousId(anonymousId);
		notificationRepository.deleteByAnonymousId(anonymousId);
		applicationRepository.flush();
		policyRepository.deleteCustomByOwner(anonymousId);
		notificationSettingsRepository.deleteById(anonymousId);
		boolean profile = repository.findByAnonymousId(anonymousId).map(p -> {
			repository.delete(p);
			return true;
		}).orElse(false);
		return profile || applications > 0;
	}

	/** 온통청년 코드는 공식 코드정의서에 있는 값만 받는다 ("제한없음" 코드는 사용자 값이 아니라 거절). */
	static void validateCodes(ProfileRequest r) {
		Map<String, String> errors = new LinkedHashMap<>();
		checkOne(errors, "jobCd", r.jobCd(), OntongCodes.JOB);
		checkOne(errors, "schoolCd", r.schoolCd(), OntongCodes.SCHOOL);
		checkOne(errors, "mrgSttsCd", r.mrgSttsCd(), OntongCodes.MARRIAGE);
		checkOne(errors, "plcyMajorCd", r.plcyMajorCd(), OntongCodes.MAJOR);
		checkMany(errors, "sbizCd", r.sbizCd(), OntongCodes.SPECIAL_GROUP.keySet());
		checkMany(errors, "interestCategories", r.interestCategories(), Categories.OFFICIAL);
		checkRegion(errors, "zipCd", r.zipCd());
		checkRegion(errors, "actualZipCd", r.actualZipCd());
		if ((r.gpa() == null) != (r.gpaScale() == null)) {
			errors.put(r.gpa() == null ? "gpa" : "gpaScale", "평점(gpa)과 만점(gpaScale)을 함께 보내 주세요");
		}
		else if (r.gpa() != null) {
			if (!GPA_SCALES.contains(r.gpaScale().stripTrailingZeros())) {
				errors.put("gpaScale", "만점은 4.0, 4.3, 4.5, 100 중 하나여야 합니다");
			}
			else if (r.gpa().compareTo(r.gpaScale()) > 0) {
				errors.put("gpa", "평점이 만점보다 클 수 없습니다");
			}
		}
		if (r.birthDate() != null && r.birthDate().isBefore(LocalDate.of(1900, 1, 1))) {
			errors.put("birthDate", "생년월일을 확인해 주세요");
		}
		if (!errors.isEmpty()) {
			throw new InvalidRequestException(errors);
		}
	}

	/** 시군구 코드는 GET /api/v1/regions 에 있는 값만 받는다 (5자리 형식 검사는 @Pattern 이 먼저 한다) */
	private static void checkRegion(Map<String, String> errors, String field, String code) {
		if (code != null && code.matches("\\d{5}") && !Regions.isSelectable(code)) {
			errors.put(field, "알 수 없는 시군구 코드: " + code);
		}
	}

	private static void checkOne(Map<String, String> errors, String field, String value, Map<String, String> codes) {
		if (value != null && !codes.containsKey(value)) {
			errors.put(field, "알 수 없는 코드: " + value);
		}
	}

	private static void checkMany(Map<String, String> errors, String field, List<String> values,
			java.util.Collection<String> allowed) {
		if (values == null) {
			return;
		}
		values.stream()
			.filter(v -> !allowed.contains(v))
			.findFirst()
			.ifPresent(v -> errors.put(field, "알 수 없는 값: " + v));
	}

	private static LocalDate today() {
		return LocalDate.now(KST);
	}

}
