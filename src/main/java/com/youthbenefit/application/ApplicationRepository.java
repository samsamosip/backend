package com.youthbenefit.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	@EntityGraph(attributePaths = "policy")
	List<Application> findAllByAnonymousIdOrderByIdDesc(UUID anonymousId);

	Optional<Application> findByAnonymousIdAndPolicyId(UUID anonymousId, Long policyId);

	Optional<Application> findByIdAndAnonymousId(Long id, UUID anonymousId);

	long deleteByAnonymousId(UUID anonymousId);

}
