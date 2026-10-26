package com.youthbenefit.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

	Optional<VerificationCode> findFirstByEmailAndPurposeOrderByIdDesc(String email, VerificationCode.Purpose purpose);

	Optional<VerificationCode> findByTicketHash(String ticketHash);

	@Modifying
	@Query("delete from VerificationCode v where v.email = :email")
	void deleteByEmail(@Param("email") String email);

}
