package com.sorted.portal.request.beans;

import lombok.Data;

@Data
public class VerifyOtpBean {

	private String otp;
	private String entity_id;
	private String reference_id;
}
