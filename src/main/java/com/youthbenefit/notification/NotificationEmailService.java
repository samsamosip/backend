package com.youthbenefit.notification;

import com.youthbenefit.auth.AuthMailer;
import com.youthbenefit.auth.User;
import com.youthbenefit.auth.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 메일 (디자인 05-b "받는 방법: 이메일"). 하루 한 번(아침 8시 매일 알림 뒤) 이메일 받기를 켠 사람에게 아직 안 보낸 알림을 한 통으로 묶어
 * 보낸다 — 알림마다 보내면 스팸 같고 스팸함으로 가기 쉬워서. 로그인했고 이메일 인증을 마친 계정만(임시 사용자는 주소가 없음). 방해금지 시간이면
 * 다음 날로 미룬다. 보낸 알림은 표시해서 다시 보내지 않는다.
 */
@Slf4j
@Service
public class NotificationEmailService {

	/** 이보다 오래된 알림은 메일로 보내지 않는다 (메일을 늦게 켰을 때 옛 알림이 쏟아지지 않게) */
	static final Duration MAX_AGE = Duration.ofDays(3);

	private final NotificationSettingsRepository settingsRepository;

	private final NotificationRepository notificationRepository;

	private final NotificationService notificationService;

	private final UserRepository users;

	private final AuthMailer mailer;

	private final TransactionTemplate tx;

	private final String webUrl;

	public NotificationEmailService(NotificationSettingsRepository settingsRepository,
			NotificationRepository notificationRepository, NotificationService notificationService, UserRepository users,
			AuthMailer mailer, TransactionTemplate tx, @Value("${app.web-url:http://localhost:5173}") String webUrl) {
		this.settingsRepository = settingsRepository;
		this.notificationRepository = notificationRepository;
		this.notificationService = notificationService;
		this.users = users;
		this.mailer = mailer;
		this.tx = tx;
		this.webUrl = webUrl.replaceAll("/+$", "");
	}

	/**
	 * @param skippedQuiet 방해금지 시간이라 미룬 사람 수
	 */
	public record Result(int mailed, int notifications, int skippedQuiet, int failed) {
	}

	public Result sendDigests(LocalDateTime now) {
		int mailed = 0;
		int notifications = 0;
		int quiet = 0;
		int failed = 0;
		for (NotificationSettings settings : settingsRepository.findAllByEmailEnabledTrue()) {
			if (inQuietHours(settings, now.toLocalTime())) {
				quiet++;
				continue;
			}
			User user = users.findByOwnerId(settings.getAnonymousId()).orElse(null);
			if (user == null || user.getEmail() == null || !user.isEmailVerified()) {
				continue;
			}
			try {
				Integer sent = tx.execute(status -> send(user.getEmail(), user.getNickname(), settings, now));
				if (sent != null && sent > 0) {
					mailed++;
					notifications += sent;
				}
			}
			catch (RuntimeException ex) {
				failed++;
				log.warn("알림 메일 실패 (계정 {}): {}", user.getId(), ex.getMessage());
			}
		}
		Result result = new Result(mailed, notifications, quiet, failed);
		log.info("알림 메일: {}", result);
		return result;
	}

	private int send(String email, String nickname, NotificationSettings settings, LocalDateTime now) {
		List<Notification> pending = notificationRepository
			.findAllByAnonymousIdAndEmailedAtIsNullAndCreatedAtAfterOrderByIdAsc(settings.getAnonymousId(),
					now.minus(MAX_AGE));
		if (pending.isEmpty()) {
			return 0;
		}
		Map<Long, String> titles = notificationService.titles(pending.stream().map(Notification::getPolicyId).toList());
		List<Line> lines = pending.stream()
			.map(n -> new Line(NotificationService.label(n), titles.get(n.getPolicyId()), notificationService.summary(n)))
			.toList();
		mailer.send(email, subject(lines), text(nickname, lines, webUrl), html(nickname, lines, webUrl));
		pending.forEach(n -> n.markEmailed(now));
		return pending.size();
	}

	/** 방해금지 시간: 22:00~08:00 처럼 자정을 넘기면 끝 시각 전이나 시작 시각 뒤 */
	static boolean inQuietHours(NotificationSettings s, LocalTime t) {
		LocalTime start = s.getQuietStart();
		LocalTime end = s.getQuietEnd();
		if (start == null || end == null || start.equals(end)) {
			return false;
		}
		return start.isBefore(end) ? !t.isBefore(start) && t.isBefore(end) : !t.isBefore(start) || t.isBefore(end);
	}

	/** 메일 한 줄: 배지(마감 임박·마감 연장·새 공고 등), 공고 제목(새 공고 묶음이면 없음), 한 줄 요약 */
	record Line(String label, String title, String summary) {
	}

	static String subject(List<Line> lines) {
		Line first = lines.get(0);
		String head = first.title() != null ? first.label() + ": " + first.title() : first.summary();
		return "[청년혜택 길잡이] " + head + (lines.size() > 1 ? " 외 " + (lines.size() - 1) + "건" : "");
	}

	static String text(String nickname, List<Line> lines, String webUrl) {
		StringBuilder b = new StringBuilder(nickname + "님, 새 알림 " + lines.size() + "건이 있어요.\n\n");
		for (Line l : lines) {
			b.append("· [").append(l.label()).append("] ");
			if (l.title() != null) {
				b.append(l.title()).append(" — ");
			}
			b.append(l.summary()).append('\n');
		}
		return b.append("\n알림함에서 보기: ")
			.append(webUrl)
			.append("/notifications\n\n알림 메일은 알림 설정에서 끌 수 있어요. 이 메일은 보내기 전용이라 답장을 받지 않아요.\n청년혜택 길잡이")
			.toString();
	}

	static String html(String nickname, List<Line> lines, String webUrl) {
		StringBuilder rows = new StringBuilder();
		for (Line l : lines) {
			rows.append("<tr><td style=\"padding:14px 0;border-top:1px solid #eef0f4\">")
				.append("<span style=\"display:inline-block;background:#eef3ff;color:#2f5bd3;border-radius:6px;padding:2px 8px;font-size:12px;font-weight:700\">")
				.append(escape(l.label()))
				.append("</span>");
			if (l.title() != null) {
				rows.append("<div style=\"padding-top:6px;font-size:15px;font-weight:700;color:#1f2937\">")
					.append(escape(l.title()))
					.append("</div>");
			}
			rows.append("<div style=\"padding-top:4px;font-size:14px;color:#4b5563\">").append(escape(l.summary())).append("</div></td></tr>");
		}
		return """
				<!doctype html><html lang="ko"><body style="margin:0;padding:0;background:#f3f6fb;font-family:'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#1f2937">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="padding:32px 16px"><tr><td align="center">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;padding:32px 28px">
				<tr><td style="font-size:15px;font-weight:700;color:#2f5bd3">청년혜택 길잡이</td></tr>
				<tr><td style="padding:20px 0 12px;font-size:20px;font-weight:700">%s님, 새 알림 %d건이 있어요</td></tr>
				%s
				<tr><td align="center" style="padding:24px 0 8px">
				<a href="%s/notifications" style="display:inline-block;background:#2f5bd3;color:#ffffff;text-decoration:none;border-radius:10px;padding:12px 24px;font-size:15px;font-weight:700">알림함에서 보기</a>
				</td></tr>
				<tr><td style="padding-top:24px;border-top:1px solid #e5e7eb;font-size:12px;line-height:1.6;color:#9ca3af">
				알림 메일은 알림 설정에서 끌 수 있어요. 이 메일은 보내기 전용이라 답장을 받지 않아요.<br>청년혜택 길잡이 — 나에게 맞는 청년 혜택을 놓치지 않게
				</td></tr>
				</table></td></tr></table></body></html>
				""".formatted(escape(nickname), lines.size(), rows, escape(webUrl));
	}

	private static String escape(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

}
