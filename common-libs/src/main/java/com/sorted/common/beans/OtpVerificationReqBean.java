package com.sorted.common.beans;

import lombok.Data;

@Data
public class OtpVerificationReqBean {

	private String otp;
	private String entity_id;
}
