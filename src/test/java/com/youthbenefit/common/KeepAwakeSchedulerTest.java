package com.youthbenefit.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KeepAwakeSchedulerTest {

	@Test
	void pingsOwnHealthAndIgnoresFailures() throws Exception {
		List<String> hits = new ArrayList<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			hits.add(exchange.getRequestURI().getPath());
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		try {
			String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
			new KeepAwakeScheduler(base, RestClient.builder()).ping();
			assertThat(hits).containsExactly("/actuator/health");
		}
		finally {
			server.stop(0);
		}
		// 서버가 없어도 예외 없이 지나간다 (다음 10분에 다시)
		new KeepAwakeScheduler("http://127.0.0.1:1", RestClient.builder()).ping();
	}

}
