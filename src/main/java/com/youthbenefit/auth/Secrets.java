package com.youthbenefit.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 토큰·코드 만들기와 저장용 해시. 원문은 사용자에게만 가고 DB 에는 SHA-256 만 남긴다. */
final class Secrets {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Secrets() {
	}

	/** 추측할 수 없는 32바이트 토큰 (URL 에 써도 되는 base64) */
	static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** 메일로 보내는 6자리 숫자 코드 */
	static String newCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	static byte[] randomBytes(int size) {
		byte[] bytes = new byte[size];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** 길이와 상관없이 같은 시간에 비교 */
	static boolean sameHash(String a, String b) {
		return a != null && b != null
				&& MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

}
