package com.sorted.portal.request.beans;

import lombok.Data;

@Data
public class VerifySignUp {

	private String entity_id;
	private String otp;
	private String reference_id;
}
