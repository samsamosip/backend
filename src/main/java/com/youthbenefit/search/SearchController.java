package com.youthbenefit.search;

import com.youthbenefit.auth.CurrentOwner;
import com.youthbenefit.common.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "자연어 검색", description = "WF06")
@RestController
@RequiredArgsConstructor
public class SearchController {

	private final SearchService service;

	@Operation(summary = "자연어 검색",
			description = "\"서울 사는 대학생 월세 지원\" 같은 문장으로 지금 신청 가능한 공고를 찾고 내 프로필로 판정한다. understood 는 알아들은 조건(화면 칩: "
					+ "keywords 단어 묶음, categories, region, age, closingSoon, ai). 기본은 불일치 제외(includeNoMatch=true 면 포함). "
					+ "결과가 없으면 message 에 안내(WF06-b). 로그인 토큰이나 X-Anonymous-Id 가 없으면 빈 프로필로 판정")
	@GetMapping("/api/v1/search")
	public SearchService.SearchResponse search(@RequestParam String q, @CurrentOwner(required = false) UUID owner,
			@RequestParam(defaultValue = "false") boolean includeNoMatch, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (q.isBlank() || q.length() > 200) {
			throw new InvalidRequestException(Map.of("q", "검색어는 1~200자로 써 주세요"));
		}
		return service.search(owner, q, includeNoMatch, Math.max(page, 0), Math.clamp(size, 1, 100));
	}

}
