package com.youthbenefit.stats;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 사용 기록 (통계용). 사람마다 하루 한 줄(방문)과 날짜별 횟수(검색·불러오기)를 남긴다. 기록이 실패해도 사용자 요청은 그대로 진행한다.
 * 같은 날 같은 사람은 메모리에서 걸러 DB 에 한 번만 쓴다.
 */
@Slf4j
@Component
public class UsageRecorder {

	public static final String SEARCH = "search";

	public static final String IMPORT_URL = "import_url";

	public static final String IMPORT_IMAGE = "import_image";

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private static final int MAX_SEEN = 50_000;

	private final NamedParameterJdbcTemplate jdbc;

	private final Clock clock;

	private volatile LocalDate seenDay;

	private final Set<UUID> seenToday = ConcurrentHashMap.newKeySet();

	public UsageRecorder(NamedParameterJdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** 오늘 이 사람이 썼다. */
	public void visit(UUID owner) {
		if (owner == null) {
			return;
		}
		LocalDate today = today();
		if (!today.equals(seenDay) || seenToday.size() > MAX_SEEN) {
			seenToday.clear();
			seenDay = today;
		}
		if (!seenToday.add(owner)) {
			return;
		}
		try {
			jdbc.update("insert into owner_activity (day, owner_id) values (:day, :owner) on conflict do nothing",
					Map.of("day", today, "owner", owner));
		}
		catch (DataAccessException ex) {
			seenToday.remove(owner);
			log.warn("방문 기록 실패: {}", ex.getMessage());
		}
	}

	/** 오늘 횟수 하나 더하기 */
	public void count(String name) {
		try {
			jdbc.update("""
					insert into daily_counts (day, name, count) values (:day, :name, 1)
					on conflict (day, name) do update set count = daily_counts.count + 1
					""", Map.of("day", today(), "name", name));
		}
		catch (DataAccessException ex) {
			log.warn("횟수 기록 실패 {}: {}", name, ex.getMessage());
		}
	}

	/** 테스트·계정 합치기 뒤 메모리 기록을 비운다 */
	public void forget() {
		seenToday.clear();
	}

	LocalDate today() {
		return LocalDate.now(clock.withZone(KST));
	}

}
