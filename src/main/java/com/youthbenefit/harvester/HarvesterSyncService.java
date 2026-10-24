package com.youthbenefit.harvester;

import com.youthbenefit.harvester.HarvesterApiClient.ChangesPage;
import com.youthbenefit.harvester.HarvesterSyncResult.Mode;
import com.youthbenefit.notification.ChangeKind;
import com.youthbenefit.notification.FieldChange;
import com.youthbenefit.notification.PolicyChangeRecorder;
import com.youthbenefit.notification.PolicyDiff;
import com.youthbenefit.policy.Policy;
import com.youthbenefit.policy.PolicyContent;
import com.youthbenefit.policy.PolicyRepository;
import com.youthbenefit.policy.PolicySource;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 학교 장학 공고를 policy-harvester 에서 받아 source = SCHOOL 로 저장한다. 상세 요청은 분당 한도가 있어서 필요한 것만 받는다.
 * <ul>
 * <li>처음(읽은 위치 없음)이나 full=true: 목록 전체를 보고 처음 보는 공고·버전이 바뀐 공고의 상세를 받는다. 목록에서 빠진 공고는
 * 사라짐으로 표시한다. 끝나면 변경 목록의 마지막 위치를 저장한다.</li>
 * <li>그다음부터: 변경 목록(/v1/changes)에서 저장한 위치 뒤의 변경만 본다. 공개가 끝난 공고(합쳐짐·비공개·삭제)는 사라짐으로 표시하고,
 * 새로 생기거나 바뀐 공고만 상세를 받는다. 상세가 실패하면 그 앞까지만 위치를 저장해 다음에 이어서 받는다.</li>
 * </ul>
 * 처음 보는 공고 중 지난 학년도보다 이전 것은 이미 끝난 장학이라 저장하지 않는다. 요청마다 따로 저장해서 긴 트랜잭션을 잡지 않는다.
 */
@Slf4j
@Service
public class HarvesterSyncService {

	static final String CURSOR_NAME = "harvester-changes";

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final HarvesterApiClient apiClient;

	private final HarvesterPolicyMapper mapper;

	private final PolicyRepository policyRepository;

	private final SyncCursorRepository cursorRepository;

	private final PolicyChangeRecorder changeRecorder;

	public HarvesterSyncService(HarvesterApiClient apiClient, HarvesterPolicyMapper mapper,
			PolicyRepository policyRepository, SyncCursorRepository cursorRepository,
			PolicyChangeRecorder changeRecorder) {
		this.apiClient = apiClient;
		this.mapper = mapper;
		this.policyRepository = policyRepository;
		this.cursorRepository = cursorRepository;
		this.changeRecorder = changeRecorder;
	}

	/** 읽은 위치가 있으면 변경만, 없으면 전체. */
	public HarvesterSyncResult sync() {
		return cursorRepository.findById(CURSOR_NAME).map(c -> syncChanges(c.getValue())).orElseGet(this::syncFull);
	}

	public HarvesterSyncResult syncFull() {
		LocalDate today = LocalDate.now(KST);
		// 목록을 받기 전 위치를 잡아 둔다. 그 사이 생긴 변경은 다음 수집 때 한 번 더 보게 된다(같은 내용이면 그대로).
		String latest = latestCursor();
		List<Map<String, Object>> items = apiClient.listAll();
		Map<String, Policy> existing = policyRepository.findAllBySource(PolicySource.SCHOOL)
			.stream()
			.collect(Collectors.toMap(Policy::getSourceId, Function.identity()));
		Counter count = new Counter();
		Set<String> listed = new HashSet<>();
		for (Map<String, Object> item : items) {
			String id = Objects.toString(item.get("opportunity_id"), null);
			if (id == null) {
				continue;
			}
			listed.add(id);
			Policy policy = existing.get(id);
			if (policy == null && isOldAcademicYear(item.get("academic_year"), today)) {
				count.skippedOld++;
				continue;
			}
			String version = Objects.toString(item.get("version_id"), null);
			if (policy != null && Objects.equals(policy.getSourceVersion(), version) && !policy.isSourceRemoved()) {
				count.unchanged++;
				continue;
			}
			fetchAndSave(id, version, null, policy, today, count);
		}
		existing.values()
			.stream()
			.filter(p -> !listed.contains(p.getSourceId()) && p.markRemoved())
			.forEach(p -> {
				policyRepository.save(p);
				changeRecorder.record(p, ChangeKind.REMOVED, List.of());
				count.removed++;
			});
		saveCursor(latest);
		return finish(Mode.FULL, items.size(), count);
	}

	HarvesterSyncResult syncChanges(String cursor) {
		LocalDate today = LocalDate.now(KST);
		List<Map<String, Object>> events = new ArrayList<>();
		String position = cursor;
		while (true) {
			ChangesPage page = apiClient.changes(position);
			if (page.items().isEmpty()) {
				break;
			}
			events.addAll(page.items());
			position = page.nextCursor();
		}
		// 한 공고에 변경이 여러 번 있으면 마지막 것만 처리한다
		Map<String, Integer> last = new HashMap<>();
		for (int i = 0; i < events.size(); i++) {
			last.put(Objects.toString(events.get(i).get("opportunity_id")), i);
		}
		Counter count = new Counter();
		String processed = cursor;
		for (int i = 0; i < events.size(); i++) {
			Map<String, Object> event = events.get(i);
			String id = Objects.toString(event.get("opportunity_id"));
			String seq = Objects.toString(event.get("event_seq"));
			if (last.get(id) != i) {
				processed = seq;
				continue;
			}
			Policy policy = policyRepository.findBySourceAndSourceId(PolicySource.SCHOOL, id).orElse(null);
			if (!Boolean.TRUE.equals(event.get("visible_after"))) {
				if (policy != null && policy.markRemoved()) {
					policyRepository.save(policy);
					changeRecorder.record(policy, ChangeKind.REMOVED, List.of());
					count.removed++;
				}
				else {
					count.unchanged++;
				}
				processed = seq;
				continue;
			}
			String version = Objects.toString(event.get("opportunity_version_id"), null);
			if (policy != null && Objects.equals(policy.getSourceVersion(), version) && !policy.isSourceRemoved()) {
				count.unchanged++;
				processed = seq;
				continue;
			}
			if (!fetchAndSave(id, version, Objects.toString(event.get("event_kind"), null), policy, today, count)) {
				break;
			}
			processed = seq;
		}
		saveCursor(processed);
		return finish(Mode.CHANGES, events.size(), count);
	}

	/**
	 * 상세를 받아 저장한다. 실패하면 false. 처음 보는 지난 학년도 공고는 저장하지 않는다. 이미 있던 공고가 바뀌면 변경을 남긴다
	 * (eventKind 는 변경 목록의 event_kind, 전체 비교 때는 null 이라 바뀐 값으로 짐작한다).
	 */
	private boolean fetchAndSave(String id, String version, String eventKind, Policy policy, LocalDate today,
			Counter count) {
		PolicyContent content;
		try {
			String detail = apiClient.detail(id);
			count.fetched++;
			if (policy == null && isOldAcademicYear(mapper.academicYear(detail), today)) {
				count.skippedOld++;
				return true;
			}
			content = mapper.toContent(Map.of("version_id", version == null ? "" : version), detail);
		}
		catch (HarvesterApiException ex) {
			log.warn("학교 장학 상세 받기 실패: {}", ex.getMessage());
			count.failed++;
			return false;
		}
		if (policy == null) {
			policyRepository.save(Policy.create(PolicySource.SCHOOL, id, content));
			count.created++;
			return true;
		}
		boolean restored = policy.isSourceRemoved();
		Policy after = Policy.create(PolicySource.SCHOOL, id, content);
		List<FieldChange> changes = PolicyDiff.diff(policy, after);
		ChangeKind kind = restored ? ChangeKind.RESTORED : kindOf(eventKind, policy, after);
		policy.markRestored();
		boolean contentChanged = policy.apply(content);
		policyRepository.save(policy);
		if (contentChanged || restored) {
			changeRecorder.record(policy, kind, changes);
			count.updated++;
		}
		else {
			count.unchanged++;
		}
		return true;
	}

	/** Harvester 가 알려 준 변경 종류. 모르는 종류면 바뀐 값으로 짐작한다. */
	private static ChangeKind kindOf(String eventKind, Policy before, Policy after) {
		return switch (eventKind == null ? "" : eventKind) {
			case "extended" -> ChangeKind.EXTENDED;
			case "corrected" -> ChangeKind.CORRECTED;
			case "cancelled" -> ChangeKind.CANCELLED;
			default -> PolicyDiff.guessKind(before, after);
		};
	}

	/** 변경 목록 끝까지 넘겨 마지막 위치를 찾는다. 변경이 하나도 없으면 "0". */
	private String latestCursor() {
		String position = "0";
		while (true) {
			ChangesPage page = apiClient.changes(position);
			if (page.items().isEmpty()) {
				return position;
			}
			position = page.nextCursor();
		}
	}

	private void saveCursor(String value) {
		SyncCursor cursor = cursorRepository.findById(CURSOR_NAME).orElseGet(() -> new SyncCursor(CURSOR_NAME, value));
		cursor.moveTo(value);
		cursorRepository.save(cursor);
	}

	private static HarvesterSyncResult finish(Mode mode, int seen, Counter c) {
		HarvesterSyncResult result = new HarvesterSyncResult(mode, seen, c.skippedOld, c.fetched, c.created, c.updated,
				c.unchanged, c.removed, c.failed);
		log.info("학교 장학 수집 완료: {}", result);
		return result;
	}

	/** 지난 학년도보다 이전(올해가 2026 이면 2024 이하) */
	static boolean isOldAcademicYear(Object academicYear, LocalDate today) {
		return academicYear instanceof Number year && year.intValue() < today.getYear() - 1;
	}

	private static final class Counter {

		int skippedOld;

		int fetched;

		int created;

		int updated;

		int unchanged;

		int removed;

		int failed;

	}

}
