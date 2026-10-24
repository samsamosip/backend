package com.youthbenefit.notification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyChangeRepository extends JpaRepository<PolicyChange, Long> {

	List<PolicyChange> findAllByPolicyIdOrderByIdDesc(Long policyId);

}
