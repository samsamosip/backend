package com.youthbenefit.auth;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 전에 쓰던 임시 ID 의 데이터를 계정으로 옮긴다. 계정에 이미 같은 것이 있으면 계정 쪽을 남긴다:
 * 프로필·알림 설정은 계정에 없을 때만 옮기고, 준비함은 같은 공고가 없을 때만, 알림은 같은 알림이 없을 때만.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountMergeService {

	private final NamedParameterJdbcTemplate jdbc;

	@Transactional
	public void merge(UUID from, UUID to) {
		Map<String, Object> p = Map.of("from", from, "to", to);
		if (exists("select count(*) from user_profiles where anonymous_id = :to", p)) {
			jdbc.update("delete from user_profiles where anonymous_id = :from", p);
		}
		else {
			jdbc.update("update user_profiles set anonymous_id = :to where anonymous_id = :from", p);
		}
		jdbc.update("""
				delete from applications a where a.anonymous_id = :from
				  and exists (select 1 from applications b where b.anonymous_id = :to and b.policy_id = a.policy_id)
				""", p);
		int apps = jdbc.update("update applications set anonymous_id = :to where anonymous_id = :from", p);
		jdbc.update("update policies set owner_id = :to where owner_id = :from", p);
		jdbc.update("update import_jobs set owner_id = :to where owner_id = :from", p);
		jdbc.update("""
				delete from notifications n where n.anonymous_id = :from and (
				  exists (select 1 from notifications m where m.anonymous_id = :to and m.policy_change_id = n.policy_change_id)
				  or exists (select 1 from notifications m where m.anonymous_id = :to and m.dedupe_key = n.dedupe_key))
				""", p);
		jdbc.update("update notifications set anonymous_id = :to where anonymous_id = :from", p);
		if (exists("select count(*) from notification_settings where anonymous_id = :to", p)) {
			jdbc.update("delete from notification_settings where anonymous_id = :from", p);
		}
		else {
			jdbc.update("update notification_settings set anonymous_id = :to where anonymous_id = :from", p);
		}
		log.info("임시 ID 데이터를 계정으로 옮김 (준비함 {}건)", apps);
	}

	private boolean exists(String countSql, Map<String, Object> params) {
		Integer n = jdbc.queryForObject(countSql, params, Integer.class);
		return n != null && n > 0;
	}

}
