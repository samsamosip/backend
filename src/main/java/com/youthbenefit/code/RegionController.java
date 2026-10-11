package com.youthbenefit.code;

import com.youthbenefit.policy.Regions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 온보딩(WF01)·내 정보(WF08)의 지역 선택: 시도를 고르면 그 시도의 시군구 목록. 저장은 시군구 code 를 zipCd 로. */
@Tag(name = "선택지", description = "온보딩 드롭다운 값")
@RestController
public class RegionController {

	public record RegionOption(String code, String name, List<CodeOption> sigungu) {
	}

	@Operation(summary = "지역 선택지", description = "시도 16개와 각 시군구. 프로필 zipCd·actualZipCd 에는 시군구 code(5자리)를 저장한다")
	@GetMapping("/api/v1/regions")
	public List<RegionOption> regions() {
		return Regions.selectableBySido()
			.entrySet()
			.stream()
			.map(e -> new RegionOption(e.getKey(), Regions.sidoName(e.getKey()),
					e.getValue().stream().map(c -> new CodeOption(c, Regions.sigunguName(c))).toList()))
			.toList();
	}

}
