package com.youthbenefit.policy;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

	List<Policy> findAllBySource(PolicySource source);

	/** 지금 신청할 수 있는 공고: 마감 코드가 아니고, 마감일이 없거나 오늘 이후. */
	@Query("""
			select p from Policy p
			where (p.applyPeriodCode is null or p.applyPeriodCode <> '0057003')
			  and (p.applyEndDate is null or p.applyEndDate >= :today)
			""")
	Page<Policy> findOpen(@Param("today") LocalDate today, Pageable pageable);

	@Query("""
			select p from Policy p
			where (p.applyPeriodCode is null or p.applyPeriodCode <> '0057003')
			  and (p.applyEndDate is null or p.applyEndDate >= :today)
			  and concat(',', p.categoryGroup, ',') like concat('%,', :category, ',%')
			""")
	Page<Policy> findOpenByCategory(@Param("today") LocalDate today, @Param("category") String category,
			Pageable pageable);

}
