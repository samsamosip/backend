package com.youthbenefit.policy;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

	/**
	 * 지금 신청할 수 있는 공고: 출처에서 사라지지 않았고, 마감 코드가 아니고, 마감일이 없거나 오늘 이후. 같은 공고가 여러 번 등록됐으면({@link Duplicates})
	 * 신청 가능한 것 중 가장 최근 등록된 하나만 남긴다.
	 */
	String OPEN = """
			p.sourceRemoved = false
			and (p.applyPeriodCode is null or p.applyPeriodCode <> '0057003')
			and (p.applyEndDate is null or p.applyEndDate >= :today)
			and not exists (
			  select 1 from Policy q
			  where q.source = p.source and q.duplicateKey = p.duplicateKey and q.id <> p.id
			    and q.sourceRemoved = false
			    and (q.applyPeriodCode is null or q.applyPeriodCode <> '0057003')
			    and (q.applyEndDate is null or q.applyEndDate >= :today)
			    and (q.sourceRegisteredAt > p.sourceRegisteredAt
			         or (q.sourceRegisteredAt = p.sourceRegisteredAt and q.id > p.id)))
			""";

	List<Policy> findAllBySource(PolicySource source);

	java.util.Optional<Policy> findBySourceAndSourceId(PolicySource source, String sourceId);

	@Query("select p from Policy p where " + OPEN)
	Page<Policy> findOpen(@Param("today") LocalDate today, Pageable pageable);

	@Query("select p from Policy p where " + OPEN
			+ " and concat(',', p.categoryGroup, ',') like concat('%,', :category, ',%')")
	Page<Policy> findOpenByCategory(@Param("today") LocalDate today, @Param("category") String category,
			Pageable pageable);

}
