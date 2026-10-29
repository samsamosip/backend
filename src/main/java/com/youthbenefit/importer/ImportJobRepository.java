package com.youthbenefit.importer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

	Optional<ImportJob> findByIdAndOwnerId(UUID id, UUID ownerId);

	@Transactional
	@Modifying
	@Query("delete from ImportJob j where j.createdAt < :before")
	int deleteCreatedBefore(Instant before);

	/** 내 데이터 삭제·탈퇴 */
	@Transactional
	@Modifying
	@Query("delete from ImportJob j where j.ownerId = :owner")
	int deleteByOwner(UUID owner);

}
