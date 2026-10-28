package com.youthbenefit.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.youthbenefit.TestcontainersConfiguration;
import com.youthbenefit.auth.AuthMailer;
import com.youthbenefit.auth.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 알림 메일: 이메일 받기를 켠 인증 계정에 하루 한 통, 다시 보내지 않기, 방해금지 시간 미루기 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIf("com.youthbenefit.DockerAvailable#isAvailable")
class NotificationEmailIntegrationTest {

	@MockitoBean
	AuthMailer mailer;

	@Autowired
	NotificationEmailService emailService;

	@Autowired
	NotificationRepository notificationRepository;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	MockMvc mockMvc;

	private String token;

	private UUID owner;

	@BeforeEach
	void setUp() throws Exception {
		jdbc.execute("delete from notifications; delete from notification_settings; delete from refresh_tokens; "
				+ "delete from verification_codes; delete from users");
		token = verifiedUser("mail@example.com");
		owner = users.findByEmail("mail@example.com").orElseThrow().getOwnerId();
	}

	@Test
	void sendsOneDigestToOptedInVerifiedAccountsOnce() throws Exception {
		LocalDateTime now = LocalDateTime.now();
		notificationRepository.save(Notification.of(owner, NotificationType.NEW_MATCHES, null,
				"{\"total\":3,\"policies\":[]}", "nm-" + UUID.randomUUID()));
		notificationRepository.save(Notification.of(owner, NotificationType.DEADLINE, null,
				"{\"applyEndDate\":\"" + LocalDate.now().plusDays(3) + "\",\"daysLeft\":3}", "dl-" + UUID.randomUUID()));

		// 이메일 받기를 안 켰으면 안 보낸다
		emailService.sendDigests(now);
		verify(mailer, never()).send(anyString(), anyString(), anyString(), anyString());

		settings(true, null, null);
		NotificationEmailService.Result r = emailService.sendDigests(now);
		assertThat(r.mailed()).isEqualTo(1);
		assertThat(r.notifications()).isEqualTo(2);
		ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(mailer).send(eq("mail@example.com"), subject.capture(), text.capture(), anyString());
		assertThat(subject.getValue()).startsWith("[청년혜택 길잡이] 내 조건에 맞는 새 공고 3건 외 1건");
		assertThat(text.getValue()).contains("새 알림 2건", "마감 3일 전이에요", "/notifications");

		// 이미 보낸 알림은 다시 안 보낸다
		emailService.sendDigests(now.plusMinutes(1));
		verify(mailer, times(1)).send(anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void quietHoursPostponeAndAnonymousUsersAreSkipped() throws Exception {
		notificationRepository.save(Notification.of(owner, NotificationType.NEW_MATCHES, null,
				"{\"total\":1,\"policies\":[]}", "nm-" + UUID.randomUUID()));
		settings(true, "22:00", "08:00");
		LocalDateTime night = LocalDate.now().atTime(23, 0);
		assertThat(emailService.sendDigests(night).skippedQuiet()).isEqualTo(1);
		verify(mailer, never()).send(anyString(), anyString(), anyString(), anyString());
		assertThat(emailService.sendDigests(LocalDate.now().atTime(8, 0)).mailed()).isEqualTo(1);

		// 로그인 안 한 사람(임시 UUID)은 주소가 없어서 안 보낸다
		String anon = UUID.randomUUID().toString();
		mockMvc.perform(put("/api/v1/me/notification-settings").header("X-Anonymous-Id", anon)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newMatches\":true,\"newMatchVerdicts\":[\"MATCH\"],\"deadline\":true,\"deadlineDays\":[1],\"changes\":true,\"email\":true}"))
			.andExpect(status().isOk());
		notificationRepository.save(Notification.of(UUID.fromString(anon), NotificationType.NEW_MATCHES, null,
				"{\"total\":1,\"policies\":[]}", "nm-" + UUID.randomUUID()));
		assertThat(emailService.sendDigests(LocalDate.now().atTime(9, 0)).mailed()).isZero();
	}

	@Test
	void quietHoursMath() {
		NotificationSettings s = NotificationSettings.defaults(UUID.randomUUID());
		assertThat(NotificationEmailService.inQuietHours(s, java.time.LocalTime.of(3, 0))).isFalse();
	}

	private void settings(boolean email, String quietStart, String quietEnd) throws Exception {
		String quiet = quietStart == null ? "" : ",\"quietStart\":\"" + quietStart + "\",\"quietEnd\":\"" + quietEnd + "\"";
		mockMvc.perform(put("/api/v1/me/notification-settings").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"newMatches\":true,\"newMatchVerdicts\":[\"MATCH\"],\"deadline\":true,\"deadlineDays\":[3],\"changes\":true,\"email\":"
					+ email + quiet + "}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));
	}

	private String verifiedUser(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"good1pass\",\"nickname\":\"메일러\"}"))
			.andExpect(status().isAccepted());
		ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
		verify(mailer).sendCode(eq(email), org.mockito.ArgumentMatchers.any(), code.capture());
		String body = mockMvc.perform(post("/api/v1/auth/email/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"code\":\"" + code.getValue() + "\"}"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

}
