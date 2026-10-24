package com.youthbenefit.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 준비함 요청 모양 모음. */
public final class ApplicationRequests {

	private ApplicationRequests() {
	}

	/** status 를 보내면 그 상태로 담는다("신청 준비 시작" → PREPARING). 없으면 관심(INTERESTED). 이미 담겨 있으면 상태만 바꾼다. */
	public record Create(@NotNull(message = "policyId 가 필요합니다") Long policyId, ApplicationStatus status) {
	}

	public record ChangeStatus(@NotNull(message = "status 가 필요합니다") ApplicationStatus status) {
	}

	public record AddItem(@NotBlank(message = "서류 이름을 입력해 주세요") @Size(max = 300) String content) {
	}

	/** 바꿀 것만 보낸다. checked 만 보내면 체크, content 만 보내면 이름 수정. */
	public record UpdateItem(Boolean checked, @Size(min = 1, max = 300) String content) {
	}

}
