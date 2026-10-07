package com.youthbenefit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class YouthBenefitBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
