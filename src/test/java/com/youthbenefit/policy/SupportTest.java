package com.youthbenefit.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupportTest {

	@Test
	void readsKoreanMoney() {
		assertThat(Support.amounts("최대 월 30만원")).containsExactly(300_000L);
		assertThat(Support.amounts("예비부부 1쌍당 5백만원")).containsExactly(5_000_000L);
		assertThat(Support.amounts("학기당 1.8백만원")).containsExactly(1_800_000L);
		assertThat(Support.amounts("1개사당 40,000천원 지원")).containsExactly(40_000_000L);
		assertThat(Support.amounts("자립정착금 1,000만원(1회)")).containsExactly(10_000_000L);
		assertThat(Support.amounts("1억 5천만원 이하")).containsExactly(150_000_000L);
		assertThat(Support.amounts("2억원")).containsExactly(200_000_000L);
		assertThat(Support.amounts("자부담 1천원 공제")).containsExactly(1_000L);
		assertThat(Support.amounts("200만 원 이내")).containsExactly(2_000_000L);
		assertThat(Support.amounts("30,000원")).containsExactly(30_000L);
		assertThat(Support.amounts("30만원/월, 20만원/월, 10만원/월")).containsExactly(300_000L, 200_000L, 100_000L);
		assertThat(Support.amounts("연 1.7% 변동금리")).isEmpty();
	}

	@Test
	void formatsLabels() {
		assertThat(Support.label(300_000, "MONTH", true, "CASH")).isEqualTo("월 최대 30만원");
		assertThat(Support.label(1_800_000, "SEMESTER", false, "CASH")).isEqualTo("학기당 180만원");
		assertThat(Support.label(10_000_000, "ONCE", false, "CASH")).isEqualTo("1,000만원");
		assertThat(Support.label(2_000_000, "YEAR", true, "LOAN")).isEqualTo("대출 연 최대 200만원");
		assertThat(Support.label(200_000_000, null, true, "INTEREST")).isEqualTo("2억원 한도 이자 지원");
		assertThat(Support.label(200_000, "ONCE", false, "VOUCHER")).isEqualTo("20만원 상당");
		assertThat(Support.won(150_000_000)).isEqualTo("1억 5,000만원");
		assertThat(Support.won(5_000)).isEqualTo("5,000원");
	}

}
