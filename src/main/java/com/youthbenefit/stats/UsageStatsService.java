package com.youthbenefit.stats;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 발표·사용자 평가용 사용 통계. 방문·검색·불러오기 횟수는 V19(11/2) 부터 쌓이고, 가입·프로필·준비함은 지금 DB 에 남아 있는 것을 센다
 * (내 데이터를 지운 사람은 빠진다).
 */
@Service
public class UsageStatsService {

	private static final int DAILY_DAYS = 14;

	private final NamedParameterJdbcTemplate jdbc;

	private final UsageRecorder recorder;

	public UsageStatsService(NamedParameterJdbcTemplate jdbc, UsageRecorder recorder) {
		this.jdbc = jdbc;
		this.recorder = recorder;
	}

	/** @param returning 이틀 이상 다른 날에 다시 쓴 사람 (재사용 근거) */
	public record Visitors(long today, long last7Days, long total, long returning) {
	}

	public record Signups(long total, long email, long kakao, long last7Days) {
	}

	/** @param people 준비함에 하나라도 담은 사람 */
	public record Applications(long people, long total, long interested, long preparing, long applied,
			long customPolicies) {
	}

	public record Counts(long today, long last7Days, long total) {
	}

	public record Day(LocalDate day, long visitors, long searches, long imports) {
	}

	public record Stats(LocalDate today, String countingSince, Visitors visitors, Signups signups, long profiles,
			Applications applications, Counts searches, Counts linkImports, Counts captureImports, List<Day> daily) {
	}

	public Stats stats() {
		LocalDate today = recorder.today();
		LocalDate weekStart = today.minusDays(6);
		Map<String, Object> p = Map.of("today", today, "weekStart", weekStart, "since",
				LocalDateTime.now().minusDays(7), "dailyStart", today.minusDays(DAILY_DAYS - 1));

		Visitors visitors = new Visitors(
				count("select count(*) from owner_activity where day = :today", p),
				count("select count(distinct owner_id) from owner_activity where day >= :weekStart", p),
				count("select count(distinct owner_id) from owner_activity", p),
				count("select count(*) from (select owner_id from owner_activity group by owner_id having count(*) >= 2) r", p));
		Signups signups = new Signups(count("select count(*) from users", p),
				count("select count(*) from users where kakao_id is null", p),
				count("select count(*) from users where kakao_id is not null", p),
				count("select count(*) from users where created_at >= :since", p));
		Applications applications = new Applications(
				count("select count(distinct anonymous_id) from applications", p),
				count("select count(*) from applications", p),
				count("select count(*) from applications where status = 'INTERESTED'", p),
				count("select count(*) from applications where status = 'PREPARING'", p),
				count("select count(*) from applications where status = 'APPLIED'", p),
				count("select count(*) from policies where owner_id is not null", p));

		Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
		for (LocalDate d = today.minusDays(DAILY_DAYS - 1); !d.isAfter(today); d = d.plusDays(1)) {
			byDay.put(d, new long[3]);
		}
		jdbc.query("select day, count(*) n from owner_activity where day >= :dailyStart group by day", p,
				rs -> {
					byDay.get(rs.getObject("day", LocalDate.class))[0] = rs.getLong("n");
				});
		jdbc.query("""
				select day, name, count from daily_counts where day >= :dailyStart
				  and name in ('search', 'import_url', 'import_image')
				""", p, rs -> {
			long[] row = byDay.get(rs.getObject("day", LocalDate.class));
			row["search".equals(rs.getString("name")) ? 1 : 2] += rs.getLong("count");
		});
		List<Day> daily = byDay.entrySet()
			.stream()
			.map(e -> new Day(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
			.toList();

		LocalDate first = jdbc.queryForObject(
				"select least((select min(day) from owner_activity), (select min(day) from daily_counts))", Map.of(),
				LocalDate.class);
		return new Stats(today, first == null ? null : first.toString(), visitors, signups,
				count("select count(*) from user_profiles", p), applications, counts(UsageRecorder.SEARCH, p),
				counts(UsageRecorder.IMPORT_URL, p), counts(UsageRecorder.IMPORT_IMAGE, p), daily);
	}

	private Counts counts(String name, Map<String, Object> p) {
		Map<String, Object> q = new LinkedHashMap<>(p);
		q.put("name", name);
		return new Counts(count("select coalesce(sum(count), 0) from daily_counts where name = :name and day = :today", q),
				count("select coalesce(sum(count), 0) from daily_counts where name = :name and day >= :weekStart", q),
				count("select coalesce(sum(count), 0) from daily_counts where name = :name", q));
	}

	private long count(String sql, Map<String, Object> params) {
		Long n = jdbc.queryForObject(sql, params, Long.class);
		return n == null ? 0 : n;
	}

}
