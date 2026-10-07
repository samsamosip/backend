package com.youthbenefit.ontong;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 수동 수집. TODO: 로그인 기능이 생기면 관리자만 호출하도록 막는다. */
@RestController
@RequestMapping("/api/v1/admin/ontong")
@RequiredArgsConstructor
public class OntongSyncController {

	private final OntongSyncService syncService;

	@PostMapping("/sync")
	public OntongSyncResult sync() {
		return syncService.sync();
	}

	@ExceptionHandler(OntongApiException.class)
	public ResponseEntity<Map<String, String>> handle(OntongApiException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", ex.getMessage()));
	}

}
