package com.youthbenefit.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("search.semantic")
public record SemanticSearchProperties(boolean enabled, double minScore, int candidateLimit, int timeoutMillis,
		int cacheSeconds, int requestsPerMinute) {
}
