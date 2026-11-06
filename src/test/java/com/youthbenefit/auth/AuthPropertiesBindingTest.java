package com.youthbenefit.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** application.yml 의 app.auth.* 가 실제로 AuthProperties 에 들어가는지 (생성자가 둘이면 Spring 이 못 고르고 mail 이 null 이 된다). */
class AuthPropertiesBindingTest {

	@Test
	void mailSettingsAreBound() {
		Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
				"app.auth.mail.host", "smtp.postmarkapp.com", "app.auth.mail.port", "587",
				"app.auth.mail.username", "PM-T-outbound-x", "app.auth.mail.password", "secret",
				"app.auth.mail.from-name", "청년혜택 길잡이", "app.auth.mail.from", "youth@gdgoc.dev")));

		AuthProperties properties = binder.bind("app.auth", AuthProperties.class).get();

		assertThat(properties.mail()).isNotNull();
		assertThat(properties.mail().username()).isEqualTo("PM-T-outbound-x");
		assertThat(properties.mail().from()).isEqualTo("youth@gdgoc.dev");
		assertThat(new AuthMailer(properties, 10).configured()).isTrue();
		assertThat(new AuthMailer(properties, 10).fromAddress()).isEqualTo("youth@gdgoc.dev");
	}

}
