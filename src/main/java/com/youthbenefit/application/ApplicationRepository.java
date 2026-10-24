package com.youthbenefit.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	@EntityGraph(attributePaths = "policy")
	List<Application> findAllByAnonymousIdOrderByIdDesc(UUID anonymousId);

	Optional<Application> findByAnonymousIdAndPolicyId(UUID anonymousId, Long policyId);

	Optional<Application> findByIdAndAnonymousId(Long id, UUID anonymousId);

	long deleteByAnonymousId(UUID anonymousId);

	/** 이 공고를 준비함에 담은 사람들 (변경 알림 대상) */
	@Query("select distinct a.anonymousId from Application a where a.policy.id = :policyId")
	List<UUID> findOwnersByPolicyId(@Param("policyId") Long policyId);

}
