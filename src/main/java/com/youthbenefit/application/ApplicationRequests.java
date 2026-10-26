package com.youthbenefit.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 준비함 요청 모양 모음. */
public final class ApplicationRequests {

	private ApplicationRequests() {
	}

	/** status 를 보내면 그 상태로 담는다("신청 준비 시작" → PREPARING). 없으면 관심(INTERESTED). 이미 담겨 있으면 상태만 바꾼다. */
	public record Create(@NotNull(message = "policyId 가 필요합니다") Long policyId, ApplicationStatus status) {
	}

	/**
	 * 공고 직접 등록·수정 (WF07). applyEndDate 가 없으면 상시, applyEndTime 은 마감일이 있을 때만("18:00"). category 는 일자리·주거·교육·
	 * 복지문화·참여권리 중 하나. documents 는 서류 이름 목록 → 체크리스트. status 는 처음 등록할 때만(기본 관심).
	 */
	public record Custom(@NotBlank(message = "공고명을 입력해 주세요") @Size(max = 100, message = "공고명은 100자 이하로 써 주세요") String title,
			@Size(max = 100, message = "기관명은 100자 이하로 써 주세요") String organization, LocalDate applyEndDate,
			LocalTime applyEndTime, String category,
			@Size(max = 2000, message = "메모는 2000자 이하로 써 주세요") String memo,
			@Size(max = 1000, message = "링크가 너무 길어요") String applyUrl,
			@Size(max = 30, message = "서류는 30개까지 넣을 수 있어요") List<@NotBlank(message = "서류 이름이 비어 있어요") @Size(max = 100, message = "서류 이름은 100자 이하로 써 주세요") String> documents,
			ApplicationStatus status) {
	}

	public record ChangeStatus(@NotNull(message = "status 가 필요합니다") ApplicationStatus status) {
	}

	public record AddItem(@NotBlank(message = "서류 이름을 입력해 주세요") @Size(max = 300) String content) {
	}

	/** 바꿀 것만 보낸다. checked 만 보내면 체크, content 만 보내면 이름 수정. */
	public record UpdateItem(Boolean checked, @Size(min = 1, max = 300) String content) {
	}

}
