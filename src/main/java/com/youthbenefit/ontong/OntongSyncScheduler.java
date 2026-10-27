package com.youthbenefit.ontong;

import com.youthbenefit.ai.LlmClient;
import com.youthbenefit.eligibility.AiConditionService;
import com.youthbenefit.support.AiSupportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** ONTONG_SYNC_SCHEDULED=true 일 때만 켜진다. 주기는 ontong.sync.cron (기본 6시간). */
@Slf4j
@Component
@ConditionalOnProperty(name = "ontong.sync.scheduled", havingValue = "true")
@RequiredArgsConstructor
public class OntongSyncScheduler {

	private final OntongSyncService syncService;

	private final AiConditionService aiConditionService;

	private final AiSupportService aiSupportService;

	private final LlmClient llm;

	@Scheduled(cron = "${ontong.sync.cron}", zone = "Asia/Seoul")
	public void run() {
		try {
			syncService.sync();
		}
		catch (OntongApiException ex) {
			log.error("온통청년 정기 수집 실패", ex);
			return;
		}
		// 새로 들어왔거나 글이 바뀐 공고만 변환한다(바뀐 게 없으면 AI 호출 없음)
		if (llm.configured()) {
			try {
				aiConditionService.convertPending(300);
				aiSupportService.extractPending(300);
			}
			catch (RuntimeException ex) {
				log.error("글 조건 AI 변환 실패", ex);
			}
		}
	}

}
