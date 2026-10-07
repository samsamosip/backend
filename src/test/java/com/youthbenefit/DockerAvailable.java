package com.youthbenefit;

import org.testcontainers.DockerClientFactory;

/** DB 통합 테스트는 Docker 가 켜져 있을 때만 돈다. 꺼져 있으면 건너뛴다. */
public final class DockerAvailable {

	private DockerAvailable() {
	}

	public static boolean isAvailable() {
		try {
			return DockerClientFactory.instance().isDockerAvailable();
		}
		catch (Throwable ex) {
			return false;
		}
	}

}
