package com.youthbenefit.harvester;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** HARVESTER_SYNC_SCHEDULED=true 일 때만 켜진다. 주기는 harvester.sync.cron (기본 매일 새벽 4시). */
@Slf4j
@Component
@ConditionalOnProperty(name = "harvester.sync.scheduled", havingValue = "true")
@RequiredArgsConstructor
public class HarvesterSyncScheduler {

	private final HarvesterSyncService syncService;

	@Scheduled(cron = "${harvester.sync.cron}", zone = "Asia/Seoul")
	public void run() {
		try {
			syncService.sync();
		}
		catch (HarvesterApiException ex) {
			log.error("학교 장학 정기 수집 실패", ex);
		}
	}

}
