package com.sorted.common.beans;

import lombok.Data;

@Data
public class VerifyOtpReqBean {

	private String otp;
	private String user_id;
}
