package com.youthbenefit.common;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키(사람·IP)마다 정해진 시간 안의 요청 수를 센다 (서버 한 대 메모리 — 다시 켜면 처음부터). 키가 너무 많아지면 오래된 기록부터 지운다.
 */
public class RateLimiter {

	private static final int MAX_KEYS = 20_000;

	private final int limit;

	private final long windowMillis;

	private final Clock clock;

	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

	public RateLimiter(int limit, Duration window, Clock clock) {
		this.limit = limit;
		this.windowMillis = window.toMillis();
		this.clock = clock;
	}

	/** 한도 안이면 한 번 세고 true, 넘었으면 세지 않고 false. limit 이 0 이하면 제한 없음 */
	public boolean tryAcquire(String key) {
		if (limit <= 0) {
			return true;
		}
		long now = clock.millis();
		if (hits.size() > MAX_KEYS) {
			hits.entrySet().removeIf(e -> {
				synchronized (e.getValue()) {
					return e.getValue().isEmpty() || e.getValue().peekLast() <= now - windowMillis;
				}
			});
		}
		Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (times) {
			while (!times.isEmpty() && times.peekFirst() <= now - windowMillis) {
				times.pollFirst();
			}
			if (times.size() >= limit) {
				return false;
			}
			times.addLast(now);
			return true;
		}
	}

}
