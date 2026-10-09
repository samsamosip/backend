package com.youthbenefit.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "준비함", description = "WF05 관심 저장, 서류 체크리스트, 신청 상태")
@RestController
@RequestMapping("/api/v1/me/applications")
@RequiredArgsConstructor
public class ApplicationController {

	private static final String OWNER = "X-Anonymous-Id";

	private final ApplicationService service;

	@Operation(summary = "관심 저장", description = "공고를 준비함에 넣고 제출 서류로 체크리스트를 만든다. 이미 있으면 200 으로 기존 것을 준다")
	@PostMapping
	public ResponseEntity<ApplicationResponses.Detail> create(@RequestHeader(OWNER) UUID owner,
			@Valid @RequestBody ApplicationRequests.Create request) {
		ApplicationService.Created result = service.create(owner, request.policyId());
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.application());
	}

	@Operation(summary = "준비함 목록", description = "최근에 넣은 순. 카드마다 상태, 마감 D-day, 준비율")
	@GetMapping
	public List<ApplicationResponses.Summary> list(@RequestHeader(OWNER) UUID owner) {
		return service.list(owner);
	}

	@Operation(summary = "준비함 상세", description = "체크리스트, 공고 제출 서류 원문, 신청 링크")
	@GetMapping("/{id}")
	public ApplicationResponses.Detail get(@RequestHeader(OWNER) UUID owner, @PathVariable Long id) {
		return service.get(owner, id);
	}

	@Operation(summary = "상태 변경", description = "INTERESTED 관심 / PREPARING 준비 중 / APPLIED 신청 완료(실제 제출 후 본인이 표시)")
	@PatchMapping("/{id}")
	public ApplicationResponses.Detail changeStatus(@RequestHeader(OWNER) UUID owner, @PathVariable Long id,
			@Valid @RequestBody ApplicationRequests.ChangeStatus request) {
		return service.changeStatus(owner, id, request.status());
	}

	@Operation(summary = "관심 해제", description = "체크리스트도 함께 지운다")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@RequestHeader(OWNER) UUID owner, @PathVariable Long id) {
		service.delete(owner, id);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "서류 추가", description = "공고에 없는 서류를 직접 추가한다")
	@PostMapping("/{id}/items")
	public ApplicationResponses.Detail addItem(@RequestHeader(OWNER) UUID owner, @PathVariable Long id,
			@Valid @RequestBody ApplicationRequests.AddItem request) {
		return service.addItem(owner, id, request.content());
	}

	@Operation(summary = "서류 체크·이름 수정", description = "{\"checked\": true} 처럼 바꿀 것만 보낸다")
	@PatchMapping("/{id}/items/{itemId}")
	public ApplicationResponses.Detail updateItem(@RequestHeader(OWNER) UUID owner, @PathVariable Long id,
			@PathVariable Long itemId, @Valid @RequestBody ApplicationRequests.UpdateItem request) {
		return service.updateItem(owner, id, itemId, request);
	}

	@Operation(summary = "서류 삭제")
	@DeleteMapping("/{id}/items/{itemId}")
	public ApplicationResponses.Detail deleteItem(@RequestHeader(OWNER) UUID owner, @PathVariable Long id,
			@PathVariable Long itemId) {
		return service.deleteItem(owner, id, itemId);
	}

}
