package com.youthbenefit.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** 불러오기 서버로 실제로 무엇이 나가는지 (작은 가짜 서버로 받아 본다). */
class ImporterClientTest {

	private HttpServer server;

	private final List<String[]> received = new ArrayList<>();

	private int status = 202;

	private ImporterClient client;

	@BeforeEach
	void setUp() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/import-jobs", exchange -> {
			received.add(new String[] { exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(),
					exchange.getRequestHeaders().getFirst("X-API-Key"), exchange.getRequestHeaders().getFirst("Content-Type"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1) });
			byte[] body = "{\"requestId\":\"x\",\"status\":\"queued\"}".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		ImporterProperties properties = new ImporterProperties("http://127.0.0.1:" + server.getAddress().getPort(),
				"backend-key", null, null, 20);
		client = new ImporterClient(properties, RestClient.builder(), JsonMapper.builder().build());
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	void linkIsSentAsJson() {
		UUID id = UUID.randomUUID();
		client.submitUrl(id, "https://example.go.kr/n/1", null);

		String[] r = received.get(0);
		assertThat(r[0]).isEqualTo("POST /v1/import-jobs");
		assertThat(r[1]).isEqualTo("backend-key");
		assertThat(r[2]).startsWith("application/json");
		assertThat(r[3]).isEqualTo("{\"requestId\":\"" + id + "\",\"type\":\"url\",\"url\":\"https://example.go.kr/n/1\"}");
	}

	@Test
	void captureIsSentAsMultipartWithImageFile() {
		UUID id = UUID.randomUUID();
		byte[] png = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };
		client.submitImages(id, List.of(new ImporterClient.Capture(png, MediaType.IMAGE_PNG)), "https://api.example/api/v1/internal/import-jobs/" + id + "/result");

		String[] r = received.get(0);
		assertThat(r[1]).isEqualTo("backend-key");
		assertThat(r[2]).startsWith("multipart/form-data");
		assertThat(r[3]).contains("name=\"requestId\"", id.toString())
			.contains("name=\"type\"", "image")
			.contains("name=\"callbackUrl\"", "/api/v1/internal/import-jobs/" + id + "/result")
			.contains("name=\"image\"; filename=\"capture-1.png\"", "Content-Type: image/png")
			.contains(new String(png, StandardCharsets.ISO_8859_1));
	}

	@Test
	void multipleCapturesUseRepeatedFieldsAndPreserveOrderAndTypes() {
		byte[] png = "first-capture".getBytes(StandardCharsets.UTF_8);
		byte[] jpeg = "second-capture".getBytes(StandardCharsets.UTF_8);
		client.submitImages(UUID.randomUUID(), List.of(new ImporterClient.Capture(png, MediaType.IMAGE_PNG),
				new ImporterClient.Capture(jpeg, MediaType.IMAGE_JPEG)), null);
		assertThat(received).hasSize(1);
		String body = received.get(0)[3];
		assertThat(body).contains("filename=\"capture-1.png\"", "filename=\"capture-2.jpeg\"",
				"Content-Type: image/png", "Content-Type: image/jpeg");
		assertThat(body.indexOf("first-capture")).isLessThan(body.indexOf("second-capture"));
	}

	@Test
	void rejectedInputIsNotRetried() {
		status = 400;
		assertThatThrownBy(() -> client.submitImages(UUID.randomUUID(), List.of(new ImporterClient.Capture(new byte[] { 1 }, MediaType.IMAGE_PNG)), null))
			.isInstanceOfSatisfying(ImporterException.class, ex -> assertThat(ex.isRejected()).isTrue());
		assertThat(received).hasSize(1);
		status = 401;
		assertThatThrownBy(() -> client.submitUrl(UUID.randomUUID(), "https://example.go.kr", null))
			.isInstanceOfSatisfying(ImporterException.class, ex -> assertThat(ex.isRejected()).isFalse());
	}

}
