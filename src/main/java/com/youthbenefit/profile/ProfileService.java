package com.youthbenefit.profile;

import com.youthbenefit.code.OntongCodes;
import com.youthbenefit.common.InvalidRequestException;
import com.youthbenefit.policy.Categories;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final UserProfileRepository repository;

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

	@Transactional
	public boolean delete(UUID anonymousId) {
		return repository.findByAnonymousId(anonymousId).map(p -> {
			repository.delete(p);
			return true;
		}).orElse(false);
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
		if (r.birthDate() != null && r.birthDate().isBefore(LocalDate.of(1900, 1, 1))) {
			errors.put("birthDate", "생년월일을 확인해 주세요");
		}
		if (!errors.isEmpty()) {
			throw new InvalidRequestException(errors);
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
