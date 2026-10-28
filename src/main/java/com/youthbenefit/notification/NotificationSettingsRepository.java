package com.youthbenefit.notification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, UUID> {

	/** 이메일로 받기를 켠 사람들 */
	java.util.List<NotificationSettings> findAllByEmailEnabledTrue();

}
