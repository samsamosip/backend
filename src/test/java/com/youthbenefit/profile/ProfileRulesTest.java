package com.youthbenefit.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.youthbenefit.common.InvalidRequestException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

	private static ProfileRequest request(LocalDate birth, String zip, String job, List<String> sbiz) {
		return new ProfileRequest(birth, zip, null, job, "0049005", null, null, sbiz, null, null, null,
				HousingType.MONTHLY_RENT, null, List.of("주거"));
	}

	private static ProfileResponse saved(ProfileRequest request) {
		UserProfile profile = UserProfile.create(UUID.randomUUID());
		profile.replace(request);
		return ProfileResponse.from(profile, TODAY);
	}

	@Test
	void ageIsInternationalAgeOnToday() {
		assertThat(saved(request(LocalDate.of(2003, 10, 7), null, null, null)).age()).isEqualTo(23);
		assertThat(saved(request(LocalDate.of(2003, 10, 8), null, null, null)).age()).isEqualTo(22);
	}

	@Test
	void residenceSidoComesFromZipCode() {
		assertThat(saved(request(null, "11620", null, null)).residenceSido()).isEqualTo("서울");
		assertThat(saved(request(null, null, null, null)).residenceSido()).isNull();
	}

	@Test
	void unknownAndNoneAreDifferent() {
		ProfileResponse unknown = saved(request(null, null, null, null));
		ProfileResponse none = saved(request(null, null, null, List.of()));

		assertThat(unknown.sbizCd()).isNull();
		assertThat(unknown.missingFields()).contains("sbizCd", "birthDate", "zipCd", "jobCd");
		assertThat(none.sbizCd()).isEmpty();
		assertThat(none.missingFields()).doesNotContain("sbizCd");
	}

	@Test
	void acceptsOfficialCodes() {
		ProfileService.validateCodes(request(null, "11620", "0013003", List.of("0014002", "0014007")));
	}

	@Test
	void rejectsUnknownAndUnrestrictedCodes() {
		// 0013010 "제한없음"은 공고 쪽 값이라 사용자 값으로 받지 않는다
		assertThatThrownBy(() -> ProfileService.validateCodes(request(null, null, "0013010", null)))
			.isInstanceOf(InvalidRequestException.class)
			.satisfies(ex -> assertThat(((InvalidRequestException) ex).errors()).containsKey("jobCd"));
		assertThatThrownBy(() -> ProfileService.validateCodes(request(null, null, null, List.of("9999999"))))
			.isInstanceOf(InvalidRequestException.class)
			.satisfies(ex -> assertThat(((InvalidRequestException) ex).errors()).containsKey("sbizCd"));
	}

}
