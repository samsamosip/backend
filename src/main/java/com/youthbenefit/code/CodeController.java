package com.youthbenefit.code;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.youthbenefit.policy.Categories;
import com.youthbenefit.profile.HousingType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 온보딩(WF01)·내 정보(WF08) 화면의 선택지. 저장할 때는 code, 보여줄 때는 label 을 쓴다. */
@Tag(name = "선택지", description = "온보딩 드롭다운 값")
@RestController
public class CodeController {

	@GetMapping("/api/v1/codes")
	public Map<String, List<CodeOption>> codes() {
		Map<String, List<CodeOption>> codes = new LinkedHashMap<>();
		codes.put("jobCd", OntongCodes.options(OntongCodes.JOB));
		codes.put("schoolCd", OntongCodes.options(OntongCodes.SCHOOL));
		codes.put("mrgSttsCd", OntongCodes.options(OntongCodes.MARRIAGE));
		codes.put("plcyMajorCd", OntongCodes.options(OntongCodes.MAJOR));
		codes.put("sbizCd", OntongCodes.options(OntongCodes.SPECIAL_GROUP));
		codes.put("housingType",
				Arrays.stream(HousingType.values()).map(h -> new CodeOption(h.name(), h.label())).toList());
		codes.put("interestCategories", Categories.OFFICIAL.stream().map(c -> new CodeOption(c, c)).toList());
		return codes;
	}

}
