package com.youthbenefit.ontong;

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

	@Scheduled(cron = "${ontong.sync.cron}", zone = "Asia/Seoul")
	public void run() {
		try {
			syncService.sync();
		}
		catch (OntongApiException ex) {
			log.error("온통청년 정기 수집 실패", ex);
		}
	}

}
