package com.youthbenefit.auth;

import com.youthbenefit.auth.AuthResponses.Account;
import com.youthbenefit.auth.AuthResponses.CodeSent;
import com.youthbenefit.auth.AuthResponses.ResetTicket;
import com.youthbenefit.auth.AuthResponses.Tokens;
import com.youthbenefit.auth.VerificationCode.Purpose;
import com.youthbenefit.common.ApiException;
import com.youthbenefit.common.InvalidRequestException;
import com.youthbenefit.profile.ProfileService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입·로그인·비밀번호 찾기·카카오 로그인. 실패해도 남겨야 하는 기록(틀린 횟수, 잠금, 코드 시도 횟수)이 있어서 ApiException 으로
 * 끝나도 되돌리지 않는다(noRollbackFor).
 *
 * <ul>
 * <li>비밀번호: 8~64자, 영문과 숫자를 함께, 특수문자 허용. BCrypt 로 저장</li>
 * <li>로그인 5번 실패 → 15분 잠금</li>
 * <li>인증 코드: 6자리, 10분, 5번 틀리면 무효, 다시 받기는 1분 뒤</li>
 * <li>비밀번호 찾기: 코드 확인 → 10분짜리 재설정 통행증 → 새 비밀번호(로그인된 기기는 모두 로그아웃)</li>
 * <li>로그인할 때 X-Anonymous-Id 가 오면 그 임시 데이터를 계정으로 옮긴다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ApiException.class)
public class AuthService {

	static final Duration CODE_TTL = Duration.ofMinutes(10);

	static final Duration RESEND_AFTER = Duration.ofMinutes(1);

	static final Duration RESET_TICKET_TTL = Duration.ofMinutes(10);

	static final int CODES_PER_EMAIL_PER_DAY = 10;

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private static final Pattern HAS_LETTER = Pattern.compile("[A-Za-z]");

	private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");

	/** 없는 이메일로 로그인해도 비밀번호 비교 시간이 같게 하려고 쓰는 가짜 해시 */
	private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("not-a-real-password-1");

	private final UserRepository users;

	private final RefreshTokenRepository refreshTokens;

	private final VerificationCodeRepository codes;

	private final TokenService tokenService;

	private final AuthMailer mailer;

	private final KakaoOAuthClient kakaoClient;

	private final AccountMergeService mergeService;

	private final ProfileService profileService;

	private final AuthProperties properties;

	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

	// ── 가입 ─────────────────────────────────────────────

	public CodeSent signup(String rawEmail, String password, String nickname) {
		String email = normalizeEmail(rawEmail);
		Map<String, String> errors = new LinkedHashMap<>();
		checkEmail(errors, email);
		checkPassword(errors, "password", password);
		if (!errors.isEmpty()) {
			throw new InvalidRequestException(errors);
		}
		User user = users.findByEmail(email).orElse(null);
		if (user != null && user.isEmailVerified()) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "이미 가입된 이메일이에요. 로그인해 주세요.");
		}
		if (user == null) {
			users.save(User.withEmail(email, encoder.encode(password), nickname.strip()));
		}
		else {
			user.restartSignup(encoder.encode(password), nickname.strip());
		}
		return sendCode(email, Purpose.SIGNUP);
	}

	/** 인증 전인 가입에만 다시 보낸다. 없는 이메일이어도 같은 응답(가입 여부를 흘리지 않게). */
	public CodeSent resendSignupCode(String rawEmail) {
		String email = normalizeEmail(rawEmail);
		User user = users.findByEmail(email).orElse(null);
		if (user != null && !user.isEmailVerified()) {
			return sendCode(email, Purpose.SIGNUP);
		}
		return new CodeSent(email, CODE_TTL.toSeconds(), RESEND_AFTER.toSeconds());
	}

	/** 코드가 맞으면 인증 완료 + 바로 로그인 */
	public Tokens verifySignup(String rawEmail, String code, UUID anonymousId) {
		String email = normalizeEmail(rawEmail);
		User user = users.findByEmail(email)
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CODE_EXPIRED", "코드가 만료됐어요. 다시 받아 주세요."));
		checkCode(email, Purpose.SIGNUP, code).consume(LocalDateTime.now());
		user.verifyEmail();
		return issue(user, anonymousId);
	}

	// ── 로그인 ───────────────────────────────────────────

	public Tokens login(String rawEmail, String password, UUID anonymousId) {
		LocalDateTime now = LocalDateTime.now();
		User user = users.findByEmail(normalizeEmail(rawEmail)).orElse(null);
		if (user == null || user.getPasswordHash() == null) {
			encoder.matches(password, DUMMY_HASH);
			throw badCredentials(null);
		}
		if (user.isLocked(now)) {
			throw locked(user, now);
		}
		if (!encoder.matches(password, user.getPasswordHash())) {
			if (user.recordFailedLogin(now)) {
				throw locked(user, now);
			}
			throw badCredentials(user.remainingAttempts());
		}
		if (!user.isEmailVerified()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "이메일 인증이 필요해요. 메일로 받은 코드를 입력해 주세요.");
		}
		user.recordSuccessfulLogin();
		return issue(user, anonymousId);
	}

	/** 카카오 로그인. 처음이면 닉네임으로 가입한다. redirectUri 는 등록해 둔 주소만 받는다. */
	public Tokens kakaoLogin(String code, String redirectUri, UUID anonymousId) {
		AuthProperties.Kakao kakao = properties.kakao();
		if (kakao == null || kakao.redirectUris() == null || !kakao.redirectUris().contains(redirectUri)) {
			throw new InvalidRequestException(Map.of("redirectUri", "등록되지 않은 주소예요: " + redirectUri));
		}
		KakaoOAuthClient.KakaoUser kakaoUser = kakaoClient.exchange(code, redirectUri);
		User user = users.findByKakaoId(kakaoUser.id())
			.orElseGet(() -> users.save(User.withKakao(kakaoUser.id(), nickname(kakaoUser.nickname()))));
		return issue(user, anonymousId);
	}

	/**
	 * 갱신 토큰으로 새 출입증을 받는다. 갱신 토큰도 매번 새로 바꾼다. 이미 바꾼(폐기된) 토큰이 다시 오면 누군가 훔쳐 쓴 것으로 보고 그
	 * 계정의 로그인을 모두 끊는다.
	 */
	public Tokens refresh(String refreshToken) {
		LocalDateTime now = LocalDateTime.now();
		RefreshToken token = refreshTokens.findByTokenHash(Secrets.sha256(refreshToken))
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "다시 로그인해 주세요."));
		if (token.isRevoked()) {
			refreshTokens.revokeAll(token.getUser().getId(), now);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_REUSED", "보안을 위해 로그아웃됐어요. 다시 로그인해 주세요.");
		}
		if (!token.getExpiresAt().isAfter(now)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "로그인 시간이 지났어요. 다시 로그인해 주세요.");
		}
		token.revoke(now);
		return issue(token.getUser(), null);
	}

	public void logout(String refreshToken) {
		refreshTokens.findByTokenHash(Secrets.sha256(refreshToken)).ifPresent(t -> t.revoke(LocalDateTime.now()));
	}

	// ── 비밀번호 찾기 ─────────────────────────────────────

	/** 가입·인증된 이메일이면 코드를 보낸다. 없는 이메일이어도 같은 응답(가입 여부를 흘리지 않게). */
	public CodeSent forgotPassword(String rawEmail) {
		String email = normalizeEmail(rawEmail);
		User user = users.findByEmail(email).orElse(null);
		if (user != null && user.getPasswordHash() != null) {
			return sendCode(email, Purpose.RESET);
		}
		return new CodeSent(email, CODE_TTL.toSeconds(), RESEND_AFTER.toSeconds());
	}

	/** 코드가 맞으면 10분짜리 재설정 통행증 */
	public ResetTicket verifyResetCode(String rawEmail, String code) {
		String email = normalizeEmail(rawEmail);
		VerificationCode verified = checkCode(email, Purpose.RESET, code);
		LocalDateTime now = LocalDateTime.now();
		verified.consume(now);
		String ticket = Secrets.newToken();
		verified.attachTicket(Secrets.sha256(ticket), now.plus(RESET_TICKET_TTL));
		return new ResetTicket(ticket, RESET_TICKET_TTL.toSeconds());
	}

	/** 새 비밀번호로 바꾸고 로그인. 다른 기기의 로그인은 모두 끊는다. */
	public Tokens resetPassword(String ticket, String newPassword, UUID anonymousId) {
		LocalDateTime now = LocalDateTime.now();
		VerificationCode verified = codes.findByTicketHash(Secrets.sha256(ticket))
			.filter(v -> v.getTicketExpiresAt() != null && v.getTicketExpiresAt().isAfter(now))
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "RESET_EXPIRED",
					"시간이 지났어요. 비밀번호 찾기를 처음부터 다시 해 주세요."));
		Map<String, String> errors = new LinkedHashMap<>();
		checkPassword(errors, "newPassword", newPassword);
		if (!errors.isEmpty()) {
			throw new InvalidRequestException(errors);
		}
		User user = users.findByEmail(verified.getEmail())
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "RESET_EXPIRED", "비밀번호 찾기를 처음부터 다시 해 주세요."));
		verified.useTicket();
		user.changePassword(encoder.encode(newPassword));
		user.verifyEmail();
		refreshTokens.revokeAll(user.getId(), now);
		return issue(user, anonymousId);
	}

	// ── 내 계정 ──────────────────────────────────────────

	@Transactional(readOnly = true)
	public Account account(long userId) {
		return Account.of(user(userId));
	}

	public Account rename(long userId, String nickname) {
		User user = user(userId);
		user.rename(nickname.strip());
		return Account.of(user);
	}

	public void changePassword(long userId, String currentPassword, String newPassword) {
		User user = user(userId);
		if (user.getPasswordHash() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NO_PASSWORD", "카카오로 가입한 계정은 비밀번호가 없어요.");
		}
		if (!encoder.matches(currentPassword, user.getPasswordHash())) {
			throw new InvalidRequestException(Map.of("currentPassword", "지금 비밀번호가 맞지 않아요"));
		}
		Map<String, String> errors = new LinkedHashMap<>();
		checkPassword(errors, "newPassword", newPassword);
		if (!errors.isEmpty()) {
			throw new InvalidRequestException(errors);
		}
		user.changePassword(encoder.encode(newPassword));
	}

	/** 회원 탈퇴: 프로필·준비함·알림·알림 설정, 인증 코드와 계정을 모두 지운다(바로 다시 가입 가능). */
	public void deleteAccount(long userId) {
		User user = user(userId);
		profileService.delete(user.getOwnerId());
		if (user.getEmail() != null) {
			codes.deleteByEmail(user.getEmail());
		}
		users.delete(user);
	}

	// ── 공통 ─────────────────────────────────────────────

	private Tokens issue(User user, UUID anonymousId) {
		if (anonymousId != null && !anonymousId.equals(user.getOwnerId()) && !users.existsByOwnerId(anonymousId)) {
			mergeService.merge(anonymousId, user.getOwnerId());
		}
		Instant now = Instant.now();
		String refresh = Secrets.newToken();
		Duration refreshTtl = Duration.ofDays(properties.refreshTokenDays());
		refreshTokens.save(new RefreshToken(user, Secrets.sha256(refresh), LocalDateTime.now().plus(refreshTtl)));
		return new Tokens(tokenService.issue(user, now), tokenService.accessTtlSeconds(), refresh, refreshTtl.toSeconds(),
				Account.of(user));
	}

	private CodeSent sendCode(String email, Purpose purpose) {
		LocalDateTime now = LocalDateTime.now();
		codes.findFirstByEmailAndPurposeOrderByIdDesc(email, purpose)
			.filter(last -> last.getCreatedAt() != null && last.getCreatedAt().plus(RESEND_AFTER).isAfter(now))
			.ifPresent(last -> {
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CODE_COOLDOWN", "코드는 1분 뒤에 다시 받을 수 있어요.");
			});
		// 한 주소에 하루 10통까지 (남의 주소로 메일 폭탄 막기)
		if (codes.countByEmailAndCreatedAtAfter(email, now.toLocalDate().atStartOfDay()) >= CODES_PER_EMAIL_PER_DAY) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CODE_DAILY_LIMIT",
					"이 주소로는 오늘 코드를 더 보낼 수 없어요. 내일 다시 해 주세요.");
		}
		// 서버 전체 하루 메일 한도를 넘었으면 코드를 만들기 전에 멈춘다(보내지도 못한 코드가 1분 제한에 걸리지 않게)
		mailer.checkCapacity();
		String code = Secrets.newCode();
		codes.save(new VerificationCode(email, purpose, Secrets.sha256(code), now.plus(CODE_TTL)));
		mailer.sendCode(email, purpose, code);
		return new CodeSent(email, CODE_TTL.toSeconds(), RESEND_AFTER.toSeconds());
	}

	/** 가장 최근 코드와 비교. 틀리면 시도 횟수를 늘리고(5번이면 무효) 남은 횟수를 알려 준다. */
	private VerificationCode checkCode(String email, Purpose purpose, String code) {
		LocalDateTime now = LocalDateTime.now();
		VerificationCode latest = codes.findFirstByEmailAndPurposeOrderByIdDesc(email, purpose)
			.filter(c -> c.isUsable(now))
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "CODE_EXPIRED", "코드가 만료됐어요. 다시 받아 주세요."));
		if (!Secrets.sameHash(latest.getCodeHash(), Secrets.sha256(code.strip()))) {
			latest.recordWrongAttempt();
			int left = VerificationCode.MAX_ATTEMPTS - latest.getAttempts();
			if (left <= 0) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_EXPIRED", "코드를 5번 틀렸어요. 다시 받아 주세요.");
			}
			throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_MISMATCH", "코드가 맞지 않아요. (남은 횟수 " + left + "번)");
		}
		return latest;
	}

	private User user(long userId) {
		return users.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "다시 로그인해 주세요."));
	}

	static String normalizeEmail(String email) {
		return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
	}

	private static void checkEmail(Map<String, String> errors, String email) {
		if (email.length() > 255 || !EMAIL.matcher(email).matches()) {
			errors.put("email", "이메일 주소를 확인해 주세요");
		}
	}

	/** 8~64자, 영문과 숫자를 함께. 특수문자는 써도 되고 안 써도 된다. */
	static void checkPassword(Map<String, String> errors, String field, String password) {
		if (password == null || password.length() < 8 || password.length() > 64 || !HAS_LETTER.matcher(password).find()
				|| !HAS_DIGIT.matcher(password).find()) {
			errors.put(field, "8자 이상, 영문과 숫자를 함께 써 주세요 (특수문자 사용 가능)");
		}
	}

	/** 닉네임 규칙(2~10자)에 맞춘다. 한 글자면 기본 이름 */
	private static String nickname(String fromKakao) {
		if (fromKakao == null || fromKakao.strip().length() < 2) {
			return "카카오 사용자";
		}
		String n = fromKakao.strip();
		return n.length() <= 10 ? n : n.substring(0, 10);
	}

	private static ApiException badCredentials(Integer remaining) {
		String suffix = remaining == null ? "" : " (남은 횟수 " + remaining + "번)";
		return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "이메일 또는 비밀번호가 맞지 않아요." + suffix);
	}

	/** 화면(08-b)에 "15:57 이후", "14분 32초 후"를 보여 줄 수 있게 풀리는 시각과 남은 초를 같이 준다 */
	private static ApiException locked(User user, LocalDateTime now) {
		LocalDateTime until = user.getLockedUntil();
		String time = until.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
		return new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
				"비밀번호를 5번 틀렸어요. " + time + " 이후에 다시 시도하거나, 비밀번호를 새로 설정해 주세요.",
				Map.of("lockedUntil", until.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString(),
						"retryAfterSeconds", Math.max(0, java.time.Duration.between(now, until).toSeconds())));
	}

}
