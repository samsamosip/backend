package com.youthbenefit.importer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.youthbenefit.common.ApiException;
import com.youthbenefit.common.InvalidRequestException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 링크 불러오기 (WF07 2차). 백엔드는 작업을 불러오기 서버에 맡기고 바로 답한다. 결과는 불러오기 서버가 콜백으로 보내 주고(서명 확인),
 * 콜백이 30초 안에 안 오거나 콜백을 못 받는 곳(로컬)이면 프론트가 상태를 물어볼 때 백엔드가 불러오기 서버에 직접 물어본다.
 * 결과는 공고 직접 등록 입력칸 모양({@link ImportPrefill})이고, 등록은 사용자가 확인한 뒤 POST /api/v1/me/custom-policies 로 한다.
 */
@Slf4j
@Service
public class ImportJobService {

	/** 콜백을 기다리는 시간. 지나면 직접 물어본다 */
	static final Duration CALLBACK_WAIT = Duration.ofSeconds(30);

	/** 직접 물어보는 최소 간격 (프론트가 2초마다 물어봐도 불러오기 서버엔 이 간격으로만) */
	static final Duration POLL_INTERVAL = Duration.ofSeconds(3);

	/** 불러오기 서버는 60초 안에 끝내고 콜백을 3번까지 다시 보낸다. 이보다 오래 걸리면 실패로 본다 */
	static final Duration GIVE_UP_AFTER = Duration.ofMinutes(3);

	/** 콜백 서명 시각이 이보다 차이 나면 거절 (다시 보내기 공격 막기) */
	static final Duration SIGNATURE_WINDOW = Duration.ofMinutes(5);

	/** 지난 작업은 일주일 뒤 지운다 */
	private static final Duration KEEP = Duration.ofDays(7);

	private static final Set<String> ERROR_CODES = Set.of("FETCH_FAILED", "NOT_A_NOTICE", "UNREADABLE_IMAGE", "TIMEOUT",
			"INTERNAL");

	private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
			"FETCH_FAILED", "링크를 열 수 없어요. 주소를 확인하거나 직접 입력해 주세요.",
			"NOT_A_NOTICE", "공고로 보이는 내용이 없어요. 직접 입력해 주세요.",
			"UNREADABLE_IMAGE", "캡처 글자를 읽을 수 없어요. 직접 입력해 주세요.",
			"TIMEOUT", "불러오는 데 너무 오래 걸려요. 다시 시도하거나 직접 입력해 주세요.",
			"INTERNAL", "지금은 불러올 수 없어요. 잠시 후 다시 하거나 직접 입력해 주세요.");

	private static final int MESSAGE_MAX = 200;

	private final ImportJobRepository repository;

	private final ImporterClient client;

	private final ImporterProperties properties;

	private final JsonMapper jsonMapper;

	private final Clock clock;

	public ImportJobService(ImportJobRepository repository, ImporterClient client, ImporterProperties properties,
			JsonMapper jsonMapper, Clock clock) {
		this.repository = repository;
		this.client = client;
		this.properties = properties;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
	}

	/** 화면용 작업 상태. prefill 은 SUCCEEDED 일 때만, error 는 FAILED 일 때만. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ImportView(UUID id, ImportJob.Status status, String url, ImportPrefill prefill, ImportError error,
			Instant createdAt) {

		static ImportView of(ImportJob job) {
			ImportError error = job.getStatus() == ImportJob.Status.FAILED
					? new ImportError(job.getErrorCode(), job.getErrorMessage()) : null;
			return new ImportView(job.getId(), job.getStatus(), job.getSourceUrl(), job.prefill(), error,
					job.getCreatedAt());
		}

	}

	/** @param message 화면에 그대로 보여 줄 한 문장 */
	public record ImportError(String code, String message) {
	}

	/** 링크 불러오기 시작. 불러오기 서버가 꺼져 있거나 설정이 없으면 503 IMPORT_UNAVAILABLE (화면은 직접 입력으로). */
	public ImportView start(UUID owner, String rawUrl) {
		String url = checkUrl(rawUrl);
		if (!properties.configured()) {
			throw unavailable();
		}
		Instant now = clock.instant();
		repository.deleteCreatedBefore(now.minus(KEEP));
		// 콜백이 작업 저장보다 먼저 올 수 있어서 먼저 저장(커밋)하고 보낸다
		ImportJob job = repository.save(ImportJob.url(owner, url, now));
		try {
			client.submitUrl(job.getId(), url, callbackUrl(job.getId()));
		}
		catch (ImporterException ex) {
			log.warn("링크 불러오기 작업 만들기 실패 {}: {}", job.getId(), ex.getMessage());
			repository.delete(job);
			throw unavailable();
		}
		return ImportView.of(job);
	}

	/** 작업 상태. 아직 안 끝났으면 필요할 때 불러오기 서버에 직접 물어본다. */
	public ImportView get(UUID owner, UUID id) {
		ImportJob job = repository.findByIdAndOwnerId(id, owner)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "불러오기 작업이 없어요: " + id));
		if (job.isDone()) {
			return ImportView.of(job);
		}
		Instant now = clock.instant();
		Duration age = Duration.between(job.getCreatedAt(), now);
		boolean waitingCallback = properties.callbackEnabled() && age.compareTo(CALLBACK_WAIT) < 0;
		boolean pollRecently = job.getPolledAt() != null
				&& Duration.between(job.getPolledAt(), now).compareTo(POLL_INTERVAL) < 0;
		if (!waitingCallback && !pollRecently) {
			job.polled(now);
			try {
				JsonNode body = client.fetch(job.getId());
				if (body == null) {
					job.fail("INTERNAL", DEFAULT_MESSAGES.get("INTERNAL"), now);
				}
				else {
					apply(job, body, now);
				}
			}
			catch (ImporterException ex) {
				log.warn("링크 불러오기 결과 조회 실패 {}: {}", job.getId(), ex.getMessage());
			}
		}
		if (!job.isDone() && age.compareTo(GIVE_UP_AFTER) >= 0) {
			job.fail("TIMEOUT", DEFAULT_MESSAGES.get("TIMEOUT"), now);
		}
		return ImportView.of(repository.save(job));
	}

	/**
	 * 불러오기 서버가 보낸 결과(콜백). 서명은 HMAC-SHA256(공유 키, "{X-Timestamp}.{본문 그대로}") 16진수. 이미 끝난 작업이면 처음 것만 쓰고
	 * 그냥 성공으로 답한다(다시 보내기 대비).
	 */
	@Transactional
	public void callback(UUID id, String timestamp, String signature, byte[] body) {
		if (!properties.callbackEnabled()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		verify(timestamp, signature, body);
		JsonNode json;
		try {
			json = jsonMapper.readTree(body);
		}
		catch (JacksonException ex) {
			throw new InvalidRequestException(Map.of("body", "JSON 이 아닙니다"));
		}
		if (!id.toString().equals(json.path("requestId").asString(""))) {
			throw new InvalidRequestException(Map.of("requestId", "주소의 작업 번호와 다릅니다"));
		}
		ImportJob job = repository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "불러오기 작업이 없어요: " + id));
		if (!job.isDone()) {
			apply(job, json, clock.instant());
		}
	}

	private void apply(ImportJob job, JsonNode body, Instant now) {
		switch (body.path("status").asString("")) {
			case "succeeded" -> job.succeed(ImportPrefill.from(body.path("result"), job.getSourceUrl()), now);
			case "failed" -> {
				JsonNode error = body.path("error");
				String code = error.path("code").asString("");
				if (!ERROR_CODES.contains(code)) {
					code = "INTERNAL";
				}
				String message = error.path("message").asString("").strip();
				if (message.isEmpty() || message.length() > MESSAGE_MAX) {
					message = DEFAULT_MESSAGES.get(code);
				}
				job.fail(code, message, now);
			}
			case "running" -> job.running();
			default -> {
				// queued 또는 모르는 값: 그대로 기다린다
			}
		}
	}

	private void verify(String timestamp, String signature, byte[] body) {
		long seconds;
		try {
			seconds = Long.parseLong(timestamp == null ? "" : timestamp.strip());
		}
		catch (NumberFormatException ex) {
			throw badSignature();
		}
		long skew = Math.abs(clock.instant().getEpochSecond() - seconds);
		if (skew > SIGNATURE_WINDOW.toSeconds() || signature == null) {
			throw badSignature();
		}
		byte[] expected = sign(properties.callbackSecret(), timestamp.strip(), body);
		byte[] given;
		try {
			given = HexFormat.of().parseHex(signature.strip().toLowerCase());
		}
		catch (IllegalArgumentException ex) {
			throw badSignature();
		}
		if (!MessageDigest.isEqual(expected, given)) {
			throw badSignature();
		}
	}

	/** HMAC-SHA256(secret, "{timestamp}.{body}") */
	static byte[] sign(String secret, String timestamp, byte[] body) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
			return mac.doFinal(body);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String callbackUrl(UUID id) {
		if (!properties.callbackEnabled()) {
			return null;
		}
		String base = properties.callbackBaseUrl().strip().replaceAll("/+$", "");
		return base + "/api/v1/internal/import-jobs/" + id + "/result";
	}

	/** http(s) 링크만. 내 컴퓨터·내부망 주소는 받지 않는다(불러오기 서버가 대신 열어 주는 걸 막기) */
	private static String checkUrl(String raw) {
		String url = raw == null ? null : raw.strip();
		if (url == null || url.isEmpty()) {
			throw new InvalidRequestException(Map.of("url", "공고 링크를 넣어 주세요"));
		}
		if (ImportPrefill.webLink(url) == null) {
			throw new InvalidRequestException(Map.of("url", "http:// 나 https:// 로 시작하는 주소를 넣어 주세요"));
		}
		if (isPrivateHost(URI.create(url).getHost())) {
			throw new InvalidRequestException(Map.of("url", "인터넷에 공개된 공고 주소를 넣어 주세요"));
		}
		return url;
	}

	static boolean isPrivateHost(String host) {
		String h = host.toLowerCase().replaceAll("^\\[|]$", "").replaceAll("\\.$", "");
		if (h.equals("localhost") || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) {
			return true;
		}
		boolean ipLiteral = h.matches("[0-9.]+") || h.contains(":");
		if (!ipLiteral) {
			return false;
		}
		try {
			InetAddress address = InetAddress.getByName(h);
			return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
					|| address.isAnyLocalAddress() || address.isMulticastAddress()
					|| h.startsWith("fc") || h.startsWith("fd");
		}
		catch (UnknownHostException ex) {
			return true;
		}
	}

	private static ApiException unavailable() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IMPORT_UNAVAILABLE",
				"지금은 링크 불러오기를 쓸 수 없어요. 직접 입력해 주세요.");
	}

	private static ApiException badSignature() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "BAD_SIGNATURE", "서명이 맞지 않습니다");
	}

}
