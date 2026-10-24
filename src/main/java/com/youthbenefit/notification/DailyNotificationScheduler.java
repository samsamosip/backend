package com.youthbenefit.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 아침(notifications.daily.cron, 기본 8시) 새 맞춤 공고·마감 임박 알림을 만든다. NOTIFICATIONS_DAILY=false 면 끈다. */
@Component
@ConditionalOnProperty(name = "notifications.daily.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DailyNotificationScheduler {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final DailyNotificationService service;

	@Scheduled(cron = "${notifications.daily.cron:0 0 8 * * *}", zone = "Asia/Seoul")
	public void run() {
		service.run(LocalDate.now(KST), LocalDateTime.now());
	}

}
