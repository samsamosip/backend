package com.youthbenefit.profile;

/** 현재 주거 형태. 온통청년에 없는 조건이라 새 이름(housingType)으로 둔다 (조건 JSON 명세 v0.1). */
public enum HousingType {

	MONTHLY_RENT("월세"),
	JEONSE("전세"),
	OWN("자가"),
	WITH_PARENTS("부모님과 거주");

	private final String label;

	HousingType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

}
