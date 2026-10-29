package com.youthbenefit.importer;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 링크 불러오기 작업 하나. id 는 불러오기 서버에 보내는 requestId. */
@Entity
@Table(name = "import_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportJob {

	public enum Type {

		URL

	}

	public enum Status {

		QUEUED, RUNNING, SUCCEEDED, FAILED

	}

	@Id
	private UUID id;

	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	private Type type;

	private String sourceUrl;

	@Enumerated(EnumType.STRING)
	private Status status;

	private String title;

	private String organization;

	private LocalDate applyEndDate;

	private LocalTime applyEndTime;

	private String category;

	private String memo;

	private String applyUrl;

	private String errorCode;

	private String errorMessage;

	private Instant createdAt;

	private Instant polledAt;

	private Instant completedAt;

	static ImportJob url(UUID ownerId, String url, Instant now) {
		ImportJob job = new ImportJob();
		job.id = UUID.randomUUID();
		job.ownerId = ownerId;
		job.type = Type.URL;
		job.sourceUrl = url;
		job.status = Status.QUEUED;
		job.createdAt = now;
		return job;
	}

	public boolean isDone() {
		return status == Status.SUCCEEDED || status == Status.FAILED;
	}

	void running() {
		if (!isDone()) {
			status = Status.RUNNING;
		}
	}

	void succeed(ImportPrefill prefill, Instant now) {
		status = Status.SUCCEEDED;
		title = prefill.title();
		organization = prefill.organization();
		applyEndDate = prefill.applyEndDate();
		applyEndTime = prefill.applyEndTime();
		category = prefill.category();
		memo = prefill.memo();
		applyUrl = prefill.applyUrl();
		completedAt = now;
	}

	void fail(String code, String message, Instant now) {
		status = Status.FAILED;
		errorCode = code;
		errorMessage = message;
		completedAt = now;
	}

	void polled(Instant now) {
		polledAt = now;
	}

	public ImportPrefill prefill() {
		return status == Status.SUCCEEDED
				? new ImportPrefill(title, organization, applyEndDate, applyEndTime, category, memo, applyUrl) : null;
	}

}
