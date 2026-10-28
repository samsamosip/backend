package com.youthbenefit.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

	Optional<VerificationCode> findFirstByEmailAndPurposeOrderByIdDesc(String email, VerificationCode.Purpose purpose);

	Optional<VerificationCode> findByTicketHash(String ticketHash);

	/** 그 주소로 그 시각 이후 보낸 코드 수 (하루 한도) */
	long countByEmailAndCreatedAtAfter(String email, java.time.LocalDateTime after);

	@Modifying
	@Query("delete from VerificationCode v where v.email = :email")
	void deleteByEmail(@Param("email") String email);

}
