package com.youthbenefit.notification;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	@EntityGraph(attributePaths = "policyChange")
	Page<Notification> findAllByAnonymousIdOrderByIdDesc(UUID anonymousId, Pageable pageable);

	@EntityGraph(attributePaths = "policyChange")
	Page<Notification> findAllByAnonymousIdAndTypeOrderByIdDesc(UUID anonymousId, NotificationType type,
			Pageable pageable);

	@EntityGraph(attributePaths = "policyChange")
	Optional<Notification> findByIdAndAnonymousId(Long id, UUID anonymousId);

	@EntityGraph(attributePaths = "policyChange")
	@Query("select n from Notification n where n.anonymousId = :owner and n.policyChange.kind in :kinds order by n.id desc")
	Page<Notification> findByKinds(@Param("owner") UUID owner, @Param("kinds") java.util.List<ChangeKind> kinds,
			Pageable pageable);

	/** 아직 메일로 안 보낸 알림 (오래된 것은 빼고) */
	@EntityGraph(attributePaths = "policyChange")
	java.util.List<Notification> findAllByAnonymousIdAndEmailedAtIsNullAndCreatedAtAfterOrderByIdAsc(UUID anonymousId,
			LocalDateTime after);

	boolean existsByAnonymousIdAndDedupeKey(UUID anonymousId, String dedupeKey);

	long countByAnonymousIdAndReadAtIsNull(UUID anonymousId);

	@Modifying
	@Query("update Notification n set n.readAt = :now where n.anonymousId = :owner and n.readAt is null")
	int markAllRead(@Param("owner") UUID owner, @Param("now") LocalDateTime now);

	long deleteByAnonymousId(UUID anonymousId);

	/** 직접 등록 공고를 지울 때 그 공고의 알림(마감 임박)도 지운다 */
	@Modifying
	@Query("delete from Notification n where n.policyId = :policyId")
	int deleteByPolicyId(@Param("policyId") Long policyId);

}
