package com.youthbenefit.harvester;

public class HarvesterApiException extends RuntimeException {

	public HarvesterApiException(String message) {
		super(message);
	}

	public HarvesterApiException(String message, Throwable cause) {
		super(message, cause);
	}

}
