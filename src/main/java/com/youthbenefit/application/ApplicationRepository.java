package com.youthbenefit.application;

import java.time.LocalDate;
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

	/** 마감일이 from~to 사이인 준비함 공고 (마감 임박 알림 대상, 신청 완료는 호출하는 쪽에서 뺀다) */
	@EntityGraph(attributePaths = "policy")
	@Query("""
			select a from Application a
			where a.policy.applyEndDate between :from and :to
			  and a.policy.sourceRemoved = false
			  and (a.policy.applyPeriodCode is null or a.policy.applyPeriodCode <> '0057003')
			""")
	List<Application> findAllWaitingWithDeadline(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** 이 공고를 준비함에 담은 사람들 (변경 알림 대상) */
	@Query("select distinct a.anonymousId from Application a where a.policy.id = :policyId")
	List<UUID> findOwnersByPolicyId(@Param("policyId") Long policyId);

}
