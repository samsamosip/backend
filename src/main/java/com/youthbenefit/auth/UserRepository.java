package com.youthbenefit.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	Optional<User> findByKakaoId(Long kakaoId);

	Optional<User> findByOwnerId(UUID ownerId);

	boolean existsByOwnerId(UUID ownerId);

}
