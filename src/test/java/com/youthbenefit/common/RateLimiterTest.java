package com.youthbenefit.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

	@Test
	void limitsPerKeyWithinWindow() {
		MovableClock clock = new MovableClock();
		RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1), clock);
		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isTrue();
		assertThat(limiter.tryAcquire("a")).isFalse();
		// 다른 사람은 따로 센다
		assertThat(limiter.tryAcquire("b")).isTrue();
		// 1분이 지나면 다시
		clock.advance(Duration.ofSeconds(61));
		assertThat(limiter.tryAcquire("a")).isTrue();
	}

	@Test
	void zeroMeansNoLimit() {
		RateLimiter limiter = new RateLimiter(0, Duration.ofMinutes(1), Clock.systemUTC());
		for (int i = 0; i < 100; i++) {
			assertThat(limiter.tryAcquire("a")).isTrue();
		}
	}

	private static final class MovableClock extends Clock {

		private Instant now = Instant.parse("2026-10-27T00:00:00Z");

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
