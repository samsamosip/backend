package com.youthbenefit.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.youthbenefit.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 출입증(access token) = HS256 JWT. 안에는 계정 id 와 데이터 키(ownerId)만 넣는다(개인정보 없음).
 * 비밀키는 JWT_SECRET. 비어 있으면 실행할 때마다 임시 키를 만든다(로컬 개발용 — 재시작하면 로그인이 풀린다).
 */
@Slf4j
@Component
public class TokenService {

	static final String ISSUER = "youth-benefit";

	public record AccessClaims(long userId, UUID ownerId) {
	}

	private final MACSigner signer;

	private final MACVerifier verifier;

	private final Duration accessTtl;

	public TokenService(AuthProperties properties) {
		byte[] key = key(properties.jwtSecret());
		try {
			this.signer = new MACSigner(key);
			this.verifier = new MACVerifier(key);
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("JWT 키를 만들 수 없습니다", ex);
		}
		this.accessTtl = Duration.ofMinutes(properties.accessTokenMinutes());
	}

	/** HS256 은 32바이트 이상의 키가 필요하다. 짧은 값은 SHA-256 으로 늘린다. */
	private static byte[] key(String secret) {
		if (secret == null || secret.isBlank()) {
			log.warn("JWT_SECRET 이 없어 임시 키를 씁니다. 서버를 다시 켜면 로그인이 풀립니다(배포 서버에는 꼭 넣으세요).");
			return Secrets.randomBytes(32);
		}
		byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
		return raw.length >= 32 ? raw : Secrets.sha256(secret).getBytes(StandardCharsets.UTF_8);
	}

	public long accessTtlSeconds() {
		return accessTtl.toSeconds();
	}

	String issue(User user, Instant now) {
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER)
			.subject(String.valueOf(user.getId()))
			.claim("owner", user.getOwnerId().toString())
			.issueTime(Date.from(now))
			.expirationTime(Date.from(now.plus(accessTtl)))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		try {
			jwt.sign(signer);
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
		return jwt.serialize();
	}

	/** 서명·발급자·만료를 확인한다. 만료면 TOKEN_EXPIRED(프론트는 갱신 토큰으로 다시 받기), 그 밖은 TOKEN_INVALID. */
	public AccessClaims verify(String token, Instant now) {
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
				throw invalid();
			}
			JWTClaimsSet claims = jwt.getJWTClaimsSet();
			if (!ISSUER.equals(claims.getIssuer()) || claims.getExpirationTime() == null) {
				throw invalid();
			}
			if (!claims.getExpirationTime().toInstant().isAfter(now)) {
				throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "로그인 시간이 지났어요. 다시 로그인해 주세요.");
			}
			return new AccessClaims(Long.parseLong(claims.getSubject()), UUID.fromString(claims.getStringClaim("owner")));
		}
		catch (ParseException | JOSEException | IllegalArgumentException ex) {
			throw invalid();
		}
	}

	private static ApiException invalid() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "로그인 정보가 올바르지 않아요. 다시 로그인해 주세요.");
	}

}
